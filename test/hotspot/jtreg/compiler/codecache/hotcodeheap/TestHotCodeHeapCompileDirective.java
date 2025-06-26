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

package compiler.codecache.hotcodeheap;

import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.NMethod;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import static jdk.test.lib.Asserts.assertEQ;
import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertNE;

import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_NONE;

abstract class TestHotCodeHeapCompileDirective {

    static final WhiteBox WB = WhiteBox.getWhiteBox();

    static void checkCompilationLevel(Method method, int level) {
        assertEQ(WB.getMethodCompilationLevel(method), level, "Compilation level");
    }

    static void clearDirectives() throws Exception {
        new JMXExecutor().execute("Compiler.directives_clear");
    }

    static void listDirectives() throws Exception {
        new JMXExecutor().execute("Compiler.directives_print");
    }

    static void waitForCompilation(Method method) {
        while (WB.isMethodQueuedForCompilation(method)) {
            Thread.onSpinWait();
        }
    }

    static void compileMethod(Method method, int level) {
        waitForCompilation(method);
        WB.enqueueMethodForCompilation(method, level);
        waitForCompilation(method);
    }

    static void compileMethodWithCheck(Method method, int level) {
        compileMethod(method, level);
        checkCompilationLevel(method, level);
    }

    static int callable() {
        final Random random = new Random();
        int result = 0;
        for (int i = 0; i < 1100; i++) {
            result += random.nextInt(100);
        }
        return result;
    }

    static abstract class Test {
        static final boolean KEEP_FILES = false;

        Path directivesPath = null;
        Method method;

        abstract String getDirectives();
        abstract void runTest() throws Exception;

        String getInitialDirectives() {
            return null;
        }

        void writeDirectives() throws Exception {
            directivesPath = Files.createTempFile("TestHotCodeHeapCompileDirective-" + this.getClass().getSimpleName(), "");
            Files.writeString(directivesPath, getDirectives().replace("$method", method.getDeclaringClass().getName() + "::" + method.getName()));
        }

        void addInitialDirectives() throws Exception {
            String dirs = getInitialDirectives();
            if (dirs != null) {
                dirs = dirs.replace("$method", method.getDeclaringClass().getName() + "::" + method.getName());
                WB.addCompilerDirective(dirs);
            }
        }

        void assertMethodData(Method method) throws Exception {
            if ((Boolean)WB.getVMFlag("ProfileInterpreter")) {
                assertNE(WB.getMethodData(method), 0L, "MethodData must be available");
            } else {
                assertEQ(WB.getMethodData(method), 0L, "MethodData must not be available");
            }
        }

        Test() {
            try {
                method = TestHotCodeHeapCompileDirective.class.getDeclaredMethod("callable");

                clearDirectives();
                compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
                assertMethodData(method);
                WB.deoptimizeMethod(method);
                WB.clearMethodState(method);
                checkCompilationLevel(method, COMP_LEVEL_NONE);
                addInitialDirectives();
                writeDirectives();
            } catch (Exception e) {
                try {
                    cleanup();
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }
                throw new RuntimeException(e);
            }
        }

        void addDirectivesWithRefreshAndLevelCheck(int level) throws Exception {
            OutputAnalyzer outputAnalyzer = new JMXExecutor().execute("Compiler.directives_add -r " + directivesPath.toString());
            outputAnalyzer.stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            waitForCompilation(method);
            assertFalse(WB.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkCompilationLevel(method, level);
        }

        void cleanup() throws Exception {
            if (directivesPath != null && !KEEP_FILES) {
                Files.deleteIfExists(directivesPath);
                directivesPath = null;
            }
        }

        final void run() throws Exception {
            try {
                runTest();
                System.out.println(this.getClass().getSimpleName() + ": PASSED");
            } catch (Exception e) {
                System.err.println(this.getClass().getSimpleName() + ": FAILED");
                listDirectives();
                throw e;
            } finally {
                cleanup();
            }
        }
    }
}
