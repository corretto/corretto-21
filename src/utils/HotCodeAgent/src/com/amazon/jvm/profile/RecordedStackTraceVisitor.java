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

package com.amazon.jvm.profile;

import java.util.HashMap;
import java.util.HashSet;

import jdk.jfr.consumer.RecordedStackTrace;

public final class RecordedStackTraceVisitor {

    private final int maxTopMethods;
    private HashMap<RecordedMethod, SamplesCounter> compiledMethodsProfile = new HashMap<>();
    private int totalSamples = 0;

    public RecordedStackTraceVisitor(int maxTopMethods) {
        this.maxTopMethods = maxTopMethods;
    }

    public void visit(RecordedStackTrace stackTrace) {
        HashSet<RecordedMethod> seenMethods = new HashSet<>();
        var it = stackTrace.getFrames().iterator();
        while (it.hasNext()) {
            var frame = it.next();
            if (frame.isJavaFrame() && "JIT compiled".equals(frame.getType())) {
                RecordedMethod method = RecordedMethod.from(frame);
                seenMethods.add(method);
                SamplesCounter counter = compiledMethodsProfile.computeIfAbsent(method, k -> new SamplesCounter());
                counter.increment();
                break;
            }
        }
        while (it.hasNext() && (seenMethods.size() != maxTopMethods)) {
            var frame = it.next();
            if (!frame.isJavaFrame() || "Interpreted".equals(frame.getType())) {
                break;
            }
            if ("JIT compiled".equals(frame.getType())) {
                RecordedMethod method = RecordedMethod.from(frame);
                if (seenMethods.add(method)) {
                    SamplesCounter counter = compiledMethodsProfile.computeIfAbsent(method, k -> new SamplesCounter());
                    counter.increment();
                }
            }
        }
        if (!seenMethods.isEmpty()) {
            totalSamples++;
        }
    }

    public void reset() {
        compiledMethodsProfile.clear();
        totalSamples = 0;
    }

    public int getTotalSamples() {
        return totalSamples;
    }

    public HashMap<RecordedMethod, SamplesCounter> getCompiledMethodsProfile() {
        return compiledMethodsProfile;
    }
}
