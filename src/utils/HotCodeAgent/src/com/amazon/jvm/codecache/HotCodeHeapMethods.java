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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.logging.Level;

import javax.management.JMException;
import javax.management.ObjectName;

import com.amazon.jvm.profile.RecordedStackTraceVisitor;
import com.amazon.jvm.profile.SamplesCounter;
import com.amazon.jvm.codecache.HotCodeAgentConfiguration.MethodExcludeList;
import com.amazon.jvm.profile.RecordedMethod;

final class HotCodeHeapMethods {

    HashMap<RecordedMethod, ProfiledState> activeMethods = new HashMap<>();

    enum UpdateResult {
        NO_CHANGE,
        FAILED,
        ADDED_METHODS,
        REMOVED_METHODS,
        ADDED_REMOVED_METHODS;
    }

    private static final class ProfiledState {

        private int notSeenCount = 0;
        private double frequency;

        ProfiledState(double frequency) {
            this.frequency = frequency;
        }

        public void markNotSeen() {
            notSeenCount++;
        }

        public void markSeen() {
            notSeenCount = 0;
        }

        public void setFrequency(double frequency) {
            this.frequency = frequency;
        }

        public double frequency() {
            return frequency;
        }

        public int notSeenInProfiles() {
            return notSeenCount;
        }
    }

    private final double minMethodFrequency;
    private final double minColdMethodsRatio;
    private final double minNewMethodsRatio;
    private final double minFreeSpaceRatio;
    private final int maxNotSeenInProfiles;
    private final MethodExcludeList methodExcludeList;

    public HotCodeHeapMethods(HotCodeAgentConfiguration config) {
        minMethodFrequency = config.hotCodeHeapMethods.minMethodFrequency();
        minColdMethodsRatio = config.hotCodeHeapMethods.minColdMethodsRatio();
        maxNotSeenInProfiles = config.hotCodeHeapMethods.maxNotSeenInProfiles();
        minNewMethodsRatio = config.hotCodeHeapMethods.minNewMethodsRatio();
        minFreeSpaceRatio = config.hotCodeHeapMethods.minFreeSpaceRatio();
        methodExcludeList = config.profiling.methodExcludeList();
    }

    private List<RecordedMethod> findColdMethodsToRemove(Map<RecordedMethod, SamplesCounter> compiledMethodsProfile, int totalSamples, int maxNotSeenInProfiles) {
        ArrayList<RecordedMethod> methodsToRemove = new ArrayList<>();
        for (var entry : activeMethods.entrySet()) {
            var method = entry.getKey();
            ProfiledState profiledState = entry.getValue();
            var samplesCounter = compiledMethodsProfile.get(method);
            if (samplesCounter != null) {
                profiledState.markSeen();
                profiledState.setFrequency((double) samplesCounter.value() / totalSamples);
                // We reset the counter to skip the method when we identify new hot methods.
                samplesCounter.reset();
            } else {
                profiledState.markNotSeen();
                if (profiledState.notSeenInProfiles() >= maxNotSeenInProfiles) {
                    // If we have not seen a method long enough in consecutive profiles,
                    // we consider it to remove from HotCodeHeap.
                    methodsToRemove.add(method);
                }
            }
        }

        return methodsToRemove;
    }

    private List<RecordedMethod> findMethodsToAdd(Map<RecordedMethod, SamplesCounter> compiledMethodsProfile, int totalSamples) {
        ArrayList<RecordedMethod> methodsToAdd = new ArrayList<>(compiledMethodsProfile.size());
        for (var entry : compiledMethodsProfile.entrySet()) {
            final var samplesCounter = entry.getValue();
            final int samples = samplesCounter.value();
            if (samples == 0) {
                continue;
            }

            final RecordedMethod method = entry.getKey();

            if (methodExcludeList.contains(method.getClassName(), method.getName())) {
                logMethodExcluded(method);
                continue;
            }

            final double frequency = (double) samples / totalSamples;
            if (frequency < minMethodFrequency) {
                logNotEnoughSamples(method, frequency, minMethodFrequency);
                continue;
            }

            methodsToAdd.add(method);
        }

        return methodsToAdd;
    }

    public UpdateResult update(RecordedStackTraceVisitor stackTraceVisitor) throws JMException, IOException {
        final var compiledMethodsProfile = stackTraceVisitor.getCompiledMethodsProfile();
        final var totalSamples = stackTraceVisitor.getTotalSamples();

        List<RecordedMethod> methodsToRemove = Collections.emptyList();
        List<RecordedMethod> methodsToAdd = Collections.emptyList();

        final var hotCodeHeapUsage = getHotCodeHeapMemoryUsage();
        if (hotCodeHeapUsage != null &&
                (hotCodeHeapUsage.getUsed() > (long)(hotCodeHeapUsage.getMax() * (1 - minFreeSpaceRatio)))) {
            final int maxNotSeenInProfiles = 1;
            methodsToRemove = findColdMethodsToRemove(compiledMethodsProfile, totalSamples, maxNotSeenInProfiles);
            logRunningOutOfSpace(hotCodeHeapUsage);
        } else {
            methodsToRemove = findColdMethodsToRemove(compiledMethodsProfile, totalSamples, maxNotSeenInProfiles);
            methodsToAdd = findMethodsToAdd(compiledMethodsProfile, totalSamples);
            if (methodsToRemove.size() < (int)(activeMethods.size() * minColdMethodsRatio)) {
                logClearedListOfColdMethods(methodsToRemove.size());
                methodsToRemove.clear();
            }

            if (methodsToAdd.size() < (int)(activeMethods.size() * minNewMethodsRatio)) {
                logClearedListOfNewMethods(methodsToAdd.size());
                methodsToAdd.clear();
            }
        }

        UpdateResult result = UpdateResult.NO_CHANGE;

        if (!methodsToRemove.isEmpty()) {
            if (!removeMethodsFromHotCodeHeap(methodsToRemove)) {
                return UpdateResult.FAILED;
            }
            for (var method : methodsToRemove) {
                var profiledState = activeMethods.remove(method);
                logRemovedColdMethod(method, profiledState);
            }
            result = UpdateResult.REMOVED_METHODS;
        }

        if (!methodsToAdd.isEmpty()) {
            if (!addMethodsToHotCodeHeap(methodsToAdd)) {
                return UpdateResult.FAILED;
            }
            for (var method : methodsToAdd) {
                final var samplesCounter = compiledMethodsProfile.get(method);
                final int samples = samplesCounter.value();
                final double frequency = (double) samples / totalSamples;
                activeMethods.put(method, new ProfiledState(frequency));
                logAddedHotMethod(method, frequency, minMethodFrequency);
            }
            result = (result == UpdateResult.NO_CHANGE) ? UpdateResult.ADDED_METHODS : UpdateResult.ADDED_REMOVED_METHODS;
        }

        logHotCodeHeapUpdate(activeMethods.size(), totalSamples, compiledMethodsProfile.size(), methodsToAdd.size(), methodsToRemove.size());

        return result;
    }

    private static void createCompilerDirective(List<RecordedMethod> methods, String directive, StringBuilder buf) {
        buf.append("[{match:[");
        methods.forEach(m -> buf.append("\"").append(m).append("\","));
        buf.append("],").append(directive).append("}]");
    }

    private static boolean invokeCompilerDirectivesAddRefresh(List<RecordedMethod> methods, String directive, StringBuilder buf) throws JMException, IOException {
        createCompilerDirective(methods, directive, buf);
        if (!invokeCompilerDirectivesAddRefresh(buf)) {
            return false;
        }
        try {
            Thread.sleep(methods.size() * 500);
        } catch (InterruptedException e) {}
        return invokeCompilerDirectivesClear();
    }

    private static boolean invokeCompilerDirectivesAddRefresh(List<RecordedMethod> methods, String directive) throws JMException, IOException {
        final int n = methods.size();
        StringBuilder buf = new StringBuilder(methods.size() * 128);
        int i;
        for (i = 100; i < n; i += 100) {
            buf.setLength(0);
            if (!invokeCompilerDirectivesAddRefresh(methods.subList(i - 100, i), directive, buf)) {
                return false;
            }
        }

        buf.setLength(0);
        return invokeCompilerDirectivesAddRefresh(methods.subList(i - 100, n), directive, buf);
    }

    private static boolean removeMethodsFromHotCodeHeap(List<RecordedMethod> methods) throws JMException, IOException {
        if (!invokeCompilerDirectivesClear()) {
            return false;
        }

        return invokeCompilerDirectivesAddRefresh(methods, "c2:{UseState:-1}");
    }

    private static boolean addMethodsToHotCodeHeap(List<RecordedMethod> methods) throws JMException, IOException {
        if (!invokeCompilerDirectivesClear()) {
            return false;
        }

        return invokeCompilerDirectivesAddRefresh(methods, "c2:{UseState:1}");
    }

    private static boolean invokeDiagnosticCommand(String cmd, Object[] args) throws JMException {
        final var beanServer = ManagementFactory.getPlatformMBeanServer();
        final String[] signature = { String[].class.getName() };
        final var result = beanServer.invoke(ObjectName.getInstance("com.sun.management:type=DiagnosticCommand"),
                                             cmd, args, signature);
        String s = (result == null) ? "" : result.toString().strip();
        if (s.isEmpty()) {
            s = "success";
        }
        logDiagnosticCommandResult(cmd, s);
        return !s.contains("error");
    }

    public static boolean invokeCompilerDirectivesClear() throws JMException {
        return invokeDiagnosticCommand("compilerDirectivesClear", new Object[] { new String[] {} });
    }

    private static boolean invokeCompilerDirectivesAddRefresh(CharSequence directives) throws JMException, IOException {
        var directivesPath = Files.createTempFile("hot-code-agent-compiler-directives", "");
        boolean result = false;
        try {
            Files.writeString(directivesPath, directives);

            var path = directivesPath.toAbsolutePath().toString();
            result = invokeDiagnosticCommand("compilerDirectivesAdd", new Object[] { new String[] { "-r", path } });
        } finally {
            if (!result && HotCodeAgent.isLoggable(Level.FINE) && Files.exists(directivesPath)) {
                String newName = directivesPath.getFileName() + ".invalid";
                var target = directivesPath.resolveSibling(newName);
                Files.move(directivesPath, target);
                HotCodeAgent.log(Level.FINE, "File with the failed compiler directive: {0}", target);
            } else {
                Files.deleteIfExists(directivesPath);
            }
        }

        return result;
    }

    private static void logNotEnoughSamples(RecordedMethod method, double frequency, double minFrequency) {
        if (!HotCodeAgent.isLoggable(Level.FINE)) {
            return;
        }

        HotCodeAgent.log(Level.FINE, "{0} frequency {1,number,#.######}, min frequency {2,number,#.######} - skipped",
                         method, frequency, minFrequency);
    }

    private static void logAddedHotMethod(RecordedMethod method, double frequency, double minFrequency) {
        if (!HotCodeAgent.isLoggable(Level.FINE)) {
            return;
        }
        HotCodeAgent.log(Level.FINE, "{0} frequency {1,number,#.######}, min frequency {2,number,#.######} - added",
                         method, frequency, minFrequency);
    }

    private static void logRemovedColdMethod(RecordedMethod method, ProfiledState profiledState) {
        if (!HotCodeAgent.isLoggable(Level.FINE)) {
            return;
        }

        HotCodeAgent.log(Level.FINE, "{0} frequency {1,number,#.######}, not seen in profiles: {2} - removed",
                         method, profiledState.frequency(), profiledState.notSeenInProfiles());
    }

    private static void logHotCodeHeapUpdate(int methodsInHotCodeHeap, int totalSamples, int processedMethods,
                                             int addedMethods, int removedMethods) {
        if (!HotCodeAgent.isLoggable(Level.INFO)) {
            return;
        }

        HotCodeAgent.log(Level.INFO, "Processed {0} methods from {1} samples: {2} added. {3} cold methods removed. Total in HotCodeHeap: {4}",
                         processedMethods, totalSamples, addedMethods, removedMethods, methodsInHotCodeHeap);
    }

    private static void logDiagnosticCommandResult(String command, String result) {
        if (!HotCodeAgent.isLoggable(Level.INFO)) {
            return;
        }
        HotCodeAgent.log(Level.INFO, "Diagnostic command {0}: {1}", command, result);
    }

    private void logRunningOutOfSpace(MemoryUsage hotCodeHeapUsage) {
        if (!HotCodeAgent.isLoggable(Level.INFO)) {
            return;
        }

        HotCodeAgent.logInfo("HotCodeHeap free space below threshold: {0} < {1}", (hotCodeHeapUsage.getMax() - hotCodeHeapUsage.getUsed()),
                             (long)(hotCodeHeapUsage.getMax() * minFreeSpaceRatio));

    }

    private void logClearedListOfColdMethods(int methodsToRemove) {
        if (!HotCodeAgent.isLoggable(Level.INFO)) {
            return;
        }
        HotCodeAgent.logInfo("Cleared a list of methods to remove. The number of cold methods below threshold: {0} < {1}",
                             methodsToRemove, (int)(activeMethods.size() * minColdMethodsRatio));

    }

    private void logClearedListOfNewMethods(int methodsToAdd) {
        if (!HotCodeAgent.isLoggable(Level.INFO)) {
            return;
        }
        HotCodeAgent.logInfo("Cleared a list of methods to add. The number of new methods below threshold: {0} < {1}",
                             methodsToAdd, (int)(activeMethods.size() * minNewMethodsRatio));
    }

    private static void logMethodExcluded(RecordedMethod method) {
        if (!HotCodeAgent.isLoggable(Level.FINE)) {
            return;
        }
        HotCodeAgent.log(Level.FINE, "{0} excluded", method);
    }

    private static MemoryUsage getHotCodeHeapMemoryUsage() {
        List<MemoryPoolMXBean> memoryPools = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean memPool : memoryPools) {
            if (memPool.isValid() && memPool.getName().contains("hot nmethods")) {
                return memPool.getUsage();
            }
        }

        return null;
    }
}
