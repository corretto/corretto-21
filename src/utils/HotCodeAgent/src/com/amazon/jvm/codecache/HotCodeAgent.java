/*
 * Copyright Amazon.com Inc. or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.amazon.jvm.codecache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import javax.management.JMException;

import com.amazon.jvm.CodeCacheStatistics;
import com.amazon.jvm.ExecutionSample;
import com.amazon.jvm.codecache.HotCodeHeapMethods.UpdateResult;
import com.amazon.jvm.profile.RecordedStackTraceVisitor;

public final class HotCodeAgent {

    private static class NullLogger extends Logger {
        NullLogger() {
            super(null, null);
        }

        @Override
        public boolean isLoggable(Level level) {
            return false;
        }
    }

    private static Logger LOGGER;

    private final HotCodeAgentConfiguration config;
    private final HotCodeHeapMethods hotCodeHeapMethods;

    private HotCodeAgent(String argumentString) throws IOException {
        config = HotCodeAgentConfiguration.from(argumentString);
        initLogger(config);
        hotCodeHeapMethods = new HotCodeHeapMethods(config);
    }

    public static void premain(String argumentString) throws IOException {
        new HotCodeAgent(argumentString).run();
    }

    private void waitUntilC2NMethodsExceedThreshold() throws InterruptedException {
        final int threshold = config.profiling.c2NMethodCount().min();
        final Duration period = config.profiling.c2NMethodCount().jfrEvent().period();
        final Duration duration = config.profiling.c2NMethodCount().jfrEvent().duration();
        final Duration pause = config.profiling.c2NMethodCount().jfrEvent().pause();
        final Duration maxWaitingTime = config.profiling.c2NMethodCount().maxWaitingTime();
        try {
            final CodeCacheStatistics codeCacheStatistics = new CodeCacheStatistics(period, duration);
            Duration totalWaitingTime = Duration.ZERO;
            logInfo("Waiting C2 compiled methods count exceeds {0}", threshold);
            logInfo("JFR event period: {0} ms, duration: {1} ms", period.toMillis(), duration.toMillis());
            logInfo("Max waiting time: {0}", maxWaitingTime);
            while (totalWaitingTime.compareTo(maxWaitingTime) < 0) {
                codeCacheStatistics.reset();
                try (var stream = codeCacheStatistics.newEventStream()) {
                    stream.start();
                }
                if (codeCacheStatistics.hasData() && codeCacheStatistics.avgC2CompiledMethodsCount() > threshold) {
                    logInfo("Finished waitUntilC2NMethodsExceedThreshold. Average C2 compiled methods count {0} > {1}", codeCacheStatistics.avgC2CompiledMethodsCount(), threshold);
                    return;
                }
                if (!codeCacheStatistics.hasData()) {
                    log(Level.FINE, "No data from C2 compiled methods count event");
                } else {
                    log(Level.FINE, "Average C2 compiled methods count {0} <= {1}", codeCacheStatistics.avgC2CompiledMethodsCount(), threshold);
                }
                totalWaitingTime = totalWaitingTime.plus(duration).plus(pause);
                logInfo("Pause: {0} ms", pause.toMillis());
                Thread.sleep(pause.toMillis());
            }
        } catch (InterruptedException e) {
            logError("Interrupted while waiting for C2 compiled methods count to exceed threshold", e);
            throw e;
        }
        logInfo("Failed to get average C2 compiled methods count > {0}", threshold);
    }

    private void moveActiveMethodsToHotCodeHeap() throws InterruptedException, JMException, IOException {
        final Duration period = config.profiling.methodSampling().jfrEvent().period();
        final Duration duration = config.profiling.methodSampling().jfrEvent().duration();
        final Duration defaultPause = config.profiling.methodSampling().jfrEvent().pause();
        final int maxPauseScale = config.profiling.methodSampling().maxPauseScale();
        final int maxTopMethods = config.profiling.methodSampling().maxTopMethods();
        logInfo("Moving active methods to HotCodeHeap");
        try {
            RecordedStackTraceVisitor recordedStackTraceVisitor = new RecordedStackTraceVisitor(maxTopMethods);

            final ExecutionSample executionSample = new ExecutionSample(period, duration,
                                                                        recordedStackTraceVisitor);

            Duration pause = defaultPause;
            logInfo("JFR event period: {0} ms, duration: {1} ms", period.toMillis(), duration.toMillis());
            while (true) {
                logCodeCache();
                recordedStackTraceVisitor.reset();
                try (var stream = executionSample.newEventStream()) {
                    stream.start();
                }
                final UpdateResult result = hotCodeHeapMethods.update(recordedStackTraceVisitor);
                if (result != UpdateResult.NO_CHANGE) {
                    pause = defaultPause;
                } else if (pause.dividedBy(defaultPause) < maxPauseScale) {
                    pause = pause.plus(defaultPause.dividedBy(3));
                }
                logInfo("Pause: {0} ms", pause.toMillis());
                Thread.sleep(pause.toMillis());
            }
        } catch (InterruptedException e) {
            logError("Interrupted while moving active methods to HotCodeHeap", e);
            throw e;
        } catch (JMException | IOException e) {
            logError("Failed to update active methods in HotCodeHeap", e);
            throw e;
        }
    }

    public void run() {
        logInfo("Running HotCodeAgent");
        Thread thread = new Thread(() -> {
            try {
                if (config.profiling.delay() != null) {
                    logInfo("Waiting for {0} ms before moving methods to HotCodeHeap", config.profiling.delay().toMillis());
                    Thread.sleep(config.profiling.delay().toMillis());
                } else {
                    waitUntilC2NMethodsExceedThreshold();
                }
                moveActiveMethodsToHotCodeHeap();
            } catch (InterruptedException | JMException | IOException e) {
                // This exception should have been logged already.
            } catch (Throwable t) {
                logError("Unexpected error in HotCodeAgent", t);
            }
        });
        thread.setName("HotCodeAgent");
        thread.setDaemon(true);
        thread.start();
    }

    static void log(Level level, String message, Object param) {
        LOGGER.log(level, message, param);
    }

    static void log(Level level, String message, Object... params) {
        LOGGER.log(level, message, params);
    }

    static void logInfo(String message, Object param) {
        LOGGER.log(Level.INFO, message, param);
    }

    static void logInfo(String message, Object param1, Object param2) {
        if (isLoggable(Level.INFO)) {
            log(Level.INFO, message, param1, param2);
        }
    }

    static void logInfo(String message, Object... params) {
        log(Level.INFO, message, params);
    }

    static void logError(String message, Throwable t) {
        LOGGER.log(Level.SEVERE, message, t);
    }

    static boolean isLoggable(Level level) {
        return LOGGER.isLoggable(level);
    }

    private static void initLogger(HotCodeAgentConfiguration config) throws IOException {
        LOGGER = new NullLogger();
        final Level level = config.logging.level();

        if (level == Level.OFF) {
            return;
        }

        Properties props = new Properties();
        props.put("java.util.logging.SimpleFormatter.format", "HotCodeAgent-Log-%4$s: %5$s [%1$tF %1$tT %1$tZ]%6$s%n");

        LogManager logManager = LogManager.getLogManager();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            props.store(out, "");
            ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
            logManager.updateConfiguration(in, (k) -> ((o, n) -> n == null ? o : n));
        }

        Handler handler = null;
        final String fileName = config.logging.fileName();
        if (fileName != null) {
            handler = new FileHandler(fileName);
            handler.setFormatter(new SimpleFormatter());
        }

        if (handler == null) {
            handler = new StreamHandler(System.out, new SimpleFormatter()) {
                @Override
                public void publish(LogRecord record) {
                    super.publish(record);
                    flush();
                }

                @Override
                public void close() {
                    flush();
                }
            };
        }

        handler.setLevel(level);
        LOGGER = Logger.getAnonymousLogger();
        LOGGER.addHandler(handler);
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(level);
    }

    private static void logCodeCache() {
        if (!isLoggable(Level.INFO)) {
            return;
        }

        List<MemoryPoolMXBean> memoryPools = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean memPool : memoryPools) {
            if (memPool.isValid() && memPool.getName().startsWith("Code")) {
                MemoryUsage memoryUsage = memPool.getUsage();
                MemoryUsage peakMemoryUsage = memPool.getPeakUsage();

                logInfo("{0}: size={1} used={2} max_used={3}",
                        memPool.getName(), memoryUsage.getMax(), memoryUsage.getUsed(), peakMemoryUsage.getUsed());
            }
        }
    }
}
