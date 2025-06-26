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

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class HotCodeAgentConfiguration {

    private static final HotCodeAgentConfiguration DEFAULT_CONFIG = new HotCodeAgentConfiguration();

    static final class MethodExcludeList {
        private static final String IDENTIFIER_PATTERN = "[a-zA-Z_$][\\w$]*";
        private static final String PACKAGE_PATTERN = "(" + IDENTIFIER_PATTERN + "\\.)*";
        private static final String CLASS_PATTERN = "(" + IDENTIFIER_PATTERN + ")";
        private static final String METHOD_PATTERN = "(" + IDENTIFIER_PATTERN + "|<init>|<clinit>)";

        private ArrayList<Pattern> patterns;
        private HashSet<String> methodNames;
        private HashMap<String, HashSet<String>> classFQNames;
        private boolean filterAll;

        MethodExcludeList() {
            patterns = new ArrayList<>();
            methodNames = new HashSet<>();
            classFQNames = new HashMap<>();
            filterAll = false;

            // There is a bug in the HotSpot:
            //    A method pattern error is reported when the method name matches the compile command name.
            String[] compileCommandNames = new String[] {
                "help",
                "quiet",
                "log",
                "print",
                "inline",
                "dontinline",
                "blackhole",
                "compileonly",
                "exclude",
                "break",
                "option",
                "unknown"
            };

            for (String name : compileCommandNames) {
                methodNames.add(name);
            }
        }

        private void load(String fileName) throws IOException {
            try (var reader = Files.newBufferedReader(Path.of(fileName))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals("*") || line.equals("*.*")) {
                        filterAll = true;
                        patterns.clear();
                        methodNames.clear();
                        classFQNames.clear();
                        break;
                    }
                    processExcludePattern(line);
                }
            }
        }

        public static MethodExcludeList fromFile(String fileName) throws IOException {
            MethodExcludeList methodExcludeList = new MethodExcludeList();
            if (fileName != null) {
                methodExcludeList.load(fileName);
            }
            return methodExcludeList;
        }

        private void processExcludePattern(String s) {
            if (s.startsWith(".") || s.endsWith(".") || s.contains("..") || !s.contains(".")) {
                throw new IllegalArgumentException("Not a class method pattern: " + s);
            }

            int pos = s.lastIndexOf('.');
            String methodName = s.substring(pos + 1);
            final String classFQName = s.substring(0, pos);

            if (classFQName.equals("*") && !methodName.contains("*")) {
                methodNames.add(methodName);
                return;
            }

            if (!classFQName.contains("*") && (methodName.equals("*") || !methodName.contains("*"))) {
                var methods = classFQNames.computeIfAbsent(classFQName, k -> new HashSet<>());
                if (!methods.contains("*")) {
                    methods.add(methodName);
                }
                return;
            }

            pos = classFQName.lastIndexOf('.');
            String className = classFQName;
            String packageName = "";
            if (pos != -1) {
                className = classFQName.substring(pos + 1);
                packageName = classFQName.substring(0, pos + 1);
            } else if (classFQName.equals("*")) {
                packageName = "*";
            }

            patterns.add(Pattern.compile(createMethodRegex(packageName, className, methodName)));
        }

        public boolean contains(String className, String methodName) {
            if (filterAll) {
                return true;
            }

            if (methodNames.contains(methodName)) {
                return true;
            }

            var methods = classFQNames.get(className);
            if (methods != null && (methods.contains(methodName) || methods.contains("*"))) {
                return true;
            }

            String fqName = className + "." + methodName;

            for (Pattern pattern : patterns) {
                if (pattern.matcher(fqName).matches()) {
                    return true;
                }
            }

            return false;
        }

        private static String toRegex(String s) {
            if (s.length() == 0) {
                return "";
            }

            String prefix = "";
            if (s.startsWith("*")) {
                prefix = "(" + IDENTIFIER_PATTERN + ")*";
                s = s.substring(1);
            }
            return prefix + s.replace("$", "\\$").replace("*", "[\\w$]*");
        }

        private static String createMethodRegex(String packageName, String className, String methodName) {
            packageName = packageName.equals("*") ?
                          PACKAGE_PATTERN :
                          toRegex(packageName.replace(".", "\\."));

            className = className.equals("*") ?
                        CLASS_PATTERN :
                        toRegex(className);

            methodName = methodName.equals("*") ?
                         METHOD_PATTERN :
                         toRegex(methodName);

            StringBuilder regexBuilder = new StringBuilder();
            regexBuilder.append('^');
            regexBuilder.append(packageName);
            regexBuilder.append(className);
            regexBuilder.append("\\.");
            regexBuilder.append(methodName);
            regexBuilder.append('$');

            return regexBuilder.toString();
        }
    }

    record Logging(Level level, String fileName) {
        Logging() {
            this(Level.OFF, null);
        }

        Logging(Properties props) {
            this(getLoggingLevel(props), props.getProperty("logging.file"));
        }
    }

    record HotCodeHeapMethods(double minMethodFrequency,
                              double minColdMethodsRatio,
                              double minNewMethodsRatio,
                              int maxNotSeenInProfiles,
                              double minFreeSpaceRatio) {

        HotCodeHeapMethods {
            checkArgNot(minMethodFrequency <= 0.0, "hotCodeHeapMethods.minMethodFrequency must be positive");
            checkArgNot(minMethodFrequency > 1.0, "hotCodeHeapMethods.minMethodFrequency must be less or equal to 1.0");
            checkArgNot(minColdMethodsRatio <= 0.0, "hotCodeHeapMethods.minColdMethodsRatio must be positive");
            checkArgNot(minColdMethodsRatio > 1.0, "hotCodeHeapMethods.minColdMethodsRatio must be less or equal to 1.0");
            checkArgNot(minNewMethodsRatio <= 0.0, "hotCodeHeapMethods.minNewMethodsRatio must be positive");
            checkArgNot(minNewMethodsRatio > 1.0, "hotCodeHeapMethods.minNewMethodsRatio must be less or equal to 1.0");
            checkArgNot(minFreeSpaceRatio <= 0.0, "hotCodeHeapMethods.minFreeSpaceRatio must be positive");
            checkArgNot(minFreeSpaceRatio > 1.0, "hotCodeHeapMethods.minFreeSpaceRatio must be less or equal to 1.0");
            checkArgNot(maxNotSeenInProfiles <= 0, "hotCodeHeapMethods.maxNotSeenInProfiles must be positive");
        }

        HotCodeHeapMethods() {
            this(0.0001,
                 0.1,
                 0.05,
                 8,
                 0.1);
        }

        HotCodeHeapMethods(Properties props) {
            this(getDoubleValue(props, "hotCodeHeapMethods.minMethodFrequency",
                                DEFAULT_CONFIG.hotCodeHeapMethods.minMethodFrequency()),
                 getDoubleValue(props, "hotCodeHeapMethods.minColdMethodsRatio",
                                DEFAULT_CONFIG.hotCodeHeapMethods.minColdMethodsRatio()),
                 getDoubleValue(props, "hotCodeHeapMethods.minNewMethodsRatio",
                                DEFAULT_CONFIG.hotCodeHeapMethods.minNewMethodsRatio()),
                 getIntValue(props, "hotCodeHeapMethods.maxNotSeenInProfiles",
                                DEFAULT_CONFIG.hotCodeHeapMethods.maxNotSeenInProfiles()),
                 getDoubleValue(props, "hotCodeHeapMethods.minFreeSpaceRatio",
                                DEFAULT_CONFIG.hotCodeHeapMethods.minFreeSpaceRatio()));
        }
    }

    record JFREvent(Duration period, Duration duration, Duration pause) {

        JFREvent {
            checkArgNot(period.compareTo(duration) >= 0, "Period must be less than duration");
        }
    }

    record C2NMethodsCount(JFREvent jfrEvent,
                            int min,
                            Duration maxWaitingTime) {

        C2NMethodsCount {
            checkArgNot(min <= 0, "profiling.c2NMethodCount.min must be positive");
        }

        C2NMethodsCount() {
            this(new JFREvent(Duration.ofSeconds(60), Duration.ofSeconds(5 * 60 + 2), Duration.ofSeconds(120)),
                 5000,
                 Duration.ofMinutes(60));
        }

        C2NMethodsCount(Properties props) {
            this(new JFREvent(getDurationValue(props, "profiling.c2NMethodCount.jfrEvent.period",
                                               DEFAULT_CONFIG.profiling.c2NMethodCount().jfrEvent().period()),
                              getDurationValue(props, "profiling.c2NMethodCount.jfrEvent.duration",
                                               DEFAULT_CONFIG.profiling.c2NMethodCount().jfrEvent().duration()),
                              getDurationValue(props, "profiling.c2NMethodCount.jfrEvent.pause",
                                               DEFAULT_CONFIG.profiling.c2NMethodCount().jfrEvent().pause())),
                 getIntValue(props, "profiling.c2NMethodCount.min",
                             DEFAULT_CONFIG.profiling.c2NMethodCount().min()),
                 getDurationValue(props, "profiling.c2NMethodCount.maxWaitingTime",
                                  DEFAULT_CONFIG.profiling.c2NMethodCount().maxWaitingTime()));
        }
    }

    record MethodSampling(JFREvent jfrEvent, int maxTopMethods, int maxPauseScale) {

        MethodSampling {
            checkArgNot(maxTopMethods <= 0, "profiling.methodSampling.maxTopMethods must be positive");
            checkArgNot(maxPauseScale <= 0, "profiling.methodSampling.maxPauseScale must be positive");
        }

        MethodSampling() {
            this(new JFREvent(Duration.ofMillis(11), Duration.ofSeconds(90), Duration.ofMinutes(8)),
                 1,
                 4);
        }

        MethodSampling(Properties props) {
            this(new JFREvent(getDurationValue(props, "profiling.methodSampling.jfrEvent.period",
                                               DEFAULT_CONFIG.profiling.methodSampling().jfrEvent().period()),
                              getDurationValue(props, "profiling.methodSampling.jfrEvent.duration",
                                               DEFAULT_CONFIG.profiling.methodSampling().jfrEvent().duration()),
                              getDurationValue(props, "profiling.methodSampling.jfrEvent.pause",
                                               DEFAULT_CONFIG.profiling.methodSampling().jfrEvent().pause())),
                 getIntValue(props, "profiling.methodSampling.maxTopMethods",
                             DEFAULT_CONFIG.profiling.methodSampling().maxTopMethods()),
                 getIntValue(props, "profiling.methodSampling.maxPauseScale",
                             DEFAULT_CONFIG.profiling.methodSampling().maxPauseScale()));
        }
    }

    record Profiling(Duration delay,
                     C2NMethodsCount c2NMethodCount,
                     MethodSampling methodSampling,
                     MethodExcludeList methodExcludeList) {
        Profiling() {
            this(null,
                 new C2NMethodsCount(),
                 new MethodSampling(),
                 new MethodExcludeList());
        }

        Profiling(Properties props) throws IOException {
            this(getDurationValue(props, "profiling.delay", DEFAULT_CONFIG.profiling.delay()),
                 new C2NMethodsCount(props),
                 new MethodSampling(props),
                 MethodExcludeList.fromFile(props.getProperty("profiling.methodExcludeList")));
        }
    }

    public final Profiling profiling;
    public final Logging logging;
    public final HotCodeHeapMethods hotCodeHeapMethods;

    private HotCodeAgentConfiguration() {
        profiling = new Profiling();
        logging = new Logging();
        hotCodeHeapMethods = new HotCodeHeapMethods();
    }

    private HotCodeAgentConfiguration(Properties props) throws IOException {
        profiling = new Profiling(props);
        logging = new Logging(props);
        hotCodeHeapMethods = new HotCodeHeapMethods(props);
    }

    private static Duration getDurationValue(Properties props, String key,
           Duration defaultValue) {
        String value = props.getProperty(key);
        return (value != null) ? parseDuration(value) : defaultValue;
    }

    private static int getIntValue(Properties props, String key,
           int defaultValue) {
        String value = props.getProperty(key);
        return (value != null) ? Integer.parseInt(value) : defaultValue;
    }

    private static double getDoubleValue(Properties props, String key,
           double defaultValue) {
        String value = props.getProperty(key);
        return (value != null) ? Double.parseDouble(value) : defaultValue;
    }

    private static Level getLoggingLevel(Properties props) {
        String value = props.getProperty("logging.level");
        return (value != null) ? Level.parse(value) : DEFAULT_CONFIG.logging.level;
    }

    public static HotCodeAgentConfiguration from(String argumentString) throws IOException {
        Properties props = parseArgs(argumentString);
        final String fileName = props.getProperty("config");
        if (fileName != null) {
            Properties config = new Properties();
            try (var is = new FileInputStream(fileName)) {
                config.load(is);
            }
            config.putAll(props);
            props = config;
        }
        return new HotCodeAgentConfiguration(props);
    }

    public static HotCodeAgentConfiguration from(Properties props) throws IOException {
        return new HotCodeAgentConfiguration(props);
    }

    private static void checkArgNot(boolean badCond, String msg) {
        if (badCond) {
            throw new IllegalArgumentException(msg);
        }
    }

    private static Properties parseArgs(String argumentString) {
        var properties = new Properties();
        if (argumentString != null) {
            var arguments = argumentString.split(",");
            for (var argument : arguments) {
                int idx = argument.indexOf('=');
                if (idx >= 0) {
                    var key = argument.substring(0, idx);
                    var value = argument.substring(idx + 1);
                    properties.put(key, value);
                } else {
                    properties.put(argument, "");
                }
            }
        }
        return properties;
    }

    private static Duration parseDuration(String str) {
        if (str.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(str.substring(0, str.length() - 2)));
        } else if (str.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(str.substring(0, str.length() - 1)));
        } else if (str.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(str.substring(0, str.length() - 1)));
        } else if (str.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(str.substring(0, str.length() - 1)));
        } else {
            throw new IllegalArgumentException("Cannot parse " + str + " as duration");
        }
    }
}
