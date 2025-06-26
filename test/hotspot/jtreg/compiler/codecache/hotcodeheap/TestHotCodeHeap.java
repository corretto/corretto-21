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

/**
 * @test TestHotCodeHeap
 * @summary Check the HotCodeHeap segment.
 * @library /test/lib
 * @requires vm.flagless
 * @run testng compiler.codecache.hotcodeheap.TestHotCodeHeap
 */
package compiler.codecache.hotcodeheap;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.annotations.Test;

public class TestHotCodeHeap {
    private static final int M = 1024 * 1024;

    private static class CodeCacheConfig {
        private boolean tieredCompilation;
        private int reservedCodeCacheSize;
        private int nonNMethodCodeHeapSize;
        private int profiledCodeHeapSize;
        private int nonProfiledCodeHeapSize;
        private int hotCodeHeapSize;

        public void turnTieredCompilationOn() {
            tieredCompilation = true;
        }

        public void turnTieredCompilationOff() {
            tieredCompilation = false;
        }

        public void setCodeCacheSize(int v) {
            reservedCodeCacheSize = v;
        }

        public void setNonNMethodCodeHeapSize(int v) {
            nonNMethodCodeHeapSize = v;
        }

        public void setProfiledCodeHeapSize(int v) {
            profiledCodeHeapSize = v;
        }

        public void setNonProfiledCodeHeapSize(int v) {
            nonProfiledCodeHeapSize = v;
        }

        public void setHotCodeHeapSize(int v) {
            hotCodeHeapSize = v;
        }

        public List<String> toJVMOptions() {
            List<String> options = new ArrayList();
            if (!tieredCompilation) {
                options.add("-XX:-TieredCompilation");
            } else {
                options.add("-XX:+TieredCompilation");
            }

            if (reservedCodeCacheSize != 0) {
                options.add("-XX:ReservedCodeCacheSize=" + reservedCodeCacheSize);
            }

            if (nonNMethodCodeHeapSize != 0) {
                options.add("-XX:NonNMethodCodeHeapSize=" + nonNMethodCodeHeapSize);
            }

            if (profiledCodeHeapSize != 0) {
                options.add("-XX:ProfiledCodeHeapSize=" + profiledCodeHeapSize);
            }

            if (nonProfiledCodeHeapSize != 0) {
                options.add("-XX:NonProfiledCodeHeapSize=" + nonProfiledCodeHeapSize);
            }

            if (hotCodeHeapSize != 0) {
                options.add("-XX:HotCodeHeapSize=" + hotCodeHeapSize);
            }

            return options;
        }
    }

    private static OutputAnalyzer runVM(CodeCacheConfig codeCacheConfig, String... vmOption) throws Exception {
        ArrayList<String> command = new ArrayList<String>();
        command.addAll(codeCacheConfig.toJVMOptions());
        command.addAll(Arrays.asList(vmOption));

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(command);
        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        return analyzer;
    }

    private static OutputAnalyzer printCodeCache(CodeCacheConfig codeCacheConfig, String... vmOptions) throws Exception {
        vmOptions = Arrays.copyOf(vmOptions, vmOptions.length + 2);
        vmOptions[vmOptions.length - 2] = "-XX:+PrintCodeCache";
        vmOptions[vmOptions.length - 1] = "-version";
        OutputAnalyzer analyzer = runVM(codeCacheConfig, vmOptions);
        analyzer.shouldHaveExitValue(0);
        return analyzer;
    }

    private static void vmFailsWith(CodeCacheConfig codeCacheConfig, String vmOption, String message) throws Exception {
        OutputAnalyzer analyzer = runVM(codeCacheConfig, vmOption, "-version");
        analyzer.shouldHaveExitValue(1);
        analyzer.shouldContain(message);
    }

    @Test
    public void testHotCodeHeap8MTieredCompilationOff() throws Exception {
        CodeCacheConfig codeCacheConfig = new CodeCacheConfig();
        codeCacheConfig.turnTieredCompilationOff();
        codeCacheConfig.setHotCodeHeapSize(8 * M);
        OutputAnalyzer analyzer = printCodeCache(codeCacheConfig);
        analyzer.shouldContain("CodeHeap 'hot nmethods': size=");
    }

    @Test
    public void testSegmentedCodeCacheOff() throws Exception {
        CodeCacheConfig codeCacheConfig = new CodeCacheConfig();
        codeCacheConfig.turnTieredCompilationOff();
        codeCacheConfig.setHotCodeHeapSize(8 * M);
        vmFailsWith(codeCacheConfig, "-XX:-SegmentedCodeCache", "HotCodeHeap requires SegmentedCodeCache enabled");
    }

    @Test
    public void testInvalidHeapSizes() throws Exception {
        CodeCacheConfig codeCacheConfig = new CodeCacheConfig();
        codeCacheConfig.setHotCodeHeapSize(8 * M);
        codeCacheConfig.turnTieredCompilationOff();
        vmFailsWith(codeCacheConfig, "-XX:NonNMethodCodeHeapSize=0",  "Improperly specified VM option 'NonNMethodCodeHeapSize=0'");
        vmFailsWith(codeCacheConfig, "-XX:NonProfiledCodeHeapSize=0", "Zero NonProfiledCodeHeapSize specified, but the CodeCache configuration requires it to be non-zero");
        codeCacheConfig.turnTieredCompilationOn();
        vmFailsWith(codeCacheConfig, "-XX:NonNMethodCodeHeapSize=0",  "Improperly specified VM option 'NonNMethodCodeHeapSize=0'");
        vmFailsWith(codeCacheConfig, "-XX:NonProfiledCodeHeapSize=0", "Zero NonProfiledCodeHeapSize specified, but the CodeCache configuration requires it to be non-zero");
        vmFailsWith(codeCacheConfig, "-XX:ProfiledCodeHeapSize=0",    "Zero ProfiledCodeHeapSize specified, but the CodeCache configuration requires it to be non-zero");
        codeCacheConfig.setHotCodeHeapSize(0);
        vmFailsWith(codeCacheConfig, "-XX:HotCodeHeapSize=0", "Improperly specified VM option 'HotCodeHeapSize=0'");
    }

    @Test
    public void testInvalidReservedCodeCacheSize() throws Exception {
        CodeCacheConfig codeCacheConfig = new CodeCacheConfig();
        codeCacheConfig.turnTieredCompilationOn();
        codeCacheConfig.setCodeCacheSize(24 * M);
        codeCacheConfig.setHotCodeHeapSize(8 * M);
        codeCacheConfig.setNonNMethodCodeHeapSize(8 * M);
        codeCacheConfig.setNonProfiledCodeHeapSize(8 * M);
        codeCacheConfig.setProfiledCodeHeapSize(8 * M);
        vmFailsWith(codeCacheConfig, "-XX:+SegmentedCodeCache", "Invalid code heap sizes");
    }

    @Test
    public void testTieredStopAtLevel() throws Exception {
        CodeCacheConfig codeCacheConfig = new CodeCacheConfig();
        codeCacheConfig.turnTieredCompilationOff();
        codeCacheConfig.setHotCodeHeapSize(8 * M);

        vmFailsWith(codeCacheConfig, "-Xint", "HotCodeHeap requires C2 enabled");
        vmFailsWith(codeCacheConfig, "-XX:TieredStopAtLevel=0", "HotCodeHeap requires C2 enabled");

        OutputAnalyzer analyzer = printCodeCache(codeCacheConfig, "-XX:TieredStopAtLevel=1");
        analyzer.shouldHaveExitValue(0);
        analyzer.shouldNotContain("CodeHeap 'profiled nmethods': size=");

        codeCacheConfig.turnTieredCompilationOn();
        vmFailsWith(codeCacheConfig, "-XX:TieredStopAtLevel=0", "HotCodeHeap requires C2 enabled");
        vmFailsWith(codeCacheConfig, "-XX:TieredStopAtLevel=1", "HotCodeHeap requires C2 enabled");
        vmFailsWith(codeCacheConfig, "-XX:TieredStopAtLevel=2", "HotCodeHeap requires C2 enabled");
        vmFailsWith(codeCacheConfig, "-XX:TieredStopAtLevel=3", "HotCodeHeap requires C2 enabled");

        analyzer = printCodeCache(codeCacheConfig, "-XX:TieredStopAtLevel=4");
        analyzer.shouldHaveExitValue(0);
        analyzer.shouldContain("CodeHeap 'profiled nmethods': size=");
    }

    static class Launcher {
        static int sink;

        public static void main(final String[] args) throws Exception {
            int end = 20_000;

            int v = 0;
            for (int i = 0; i < end; i++) {
                v += test(i);
            }
            sink = v;
        }

        // This method is tagged for HotCodeHeap by a compile command.
        // We should see this in the PrintCompilation output.
        static int test(int i) {
            return i;
        }
    }
}
