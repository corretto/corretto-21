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
 * @test TestHotCodeHeapCompileDirective02
 * @summary Test marking Java methods with compiler directive UseState has no effect if HotCodeHeap is not available
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xlog:codecache=trace -XX:+TieredCompilation
 *                   -XX:+ProfileInterpreter -XX:+ProfileTraps
 *                   compiler.codecache.hotcodeheap.TestHotCodeHeapCompileDirective02
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xlog:codecache=trace -XX:-TieredCompilation
 *                   -XX:+ProfileInterpreter -XX:+ProfileTraps
 *                   compiler.codecache.hotcodeheap.TestHotCodeHeapCompileDirective02
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xlog:codecache=trace -XX:+TieredCompilation -XX:+RecompileColdNMethods
 *                   -XX:+ProfileInterpreter -XX:+ProfileTraps
 *                   compiler.codecache.hotcodeheap.TestHotCodeHeapCompileDirective02
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xlog:codecache=trace -XX:-TieredCompilation -XX:+RecompileColdNMethods
 *                   -XX:+ProfileInterpreter -XX:+ProfileTraps
 *                   compiler.codecache.hotcodeheap.TestHotCodeHeapCompileDirective02
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xlog:codecache=trace -XX:+TieredCompilation
 *                   -XX:+ProfileInterpreter -XX:+ProfileTraps
 *                   -XX:CompileCommand=HotCodeHeap,compiler/codecache/hotcodeheap/TestHotCodeHeapCompileDirective.callable
 *                   compiler.codecache.hotcodeheap.TestHotCodeHeapCompileDirective02
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xlog:codecache=trace -XX:-TieredCompilation
 *                   -XX:+ProfileInterpreter -XX:+ProfileTraps
 *                   -XX:CompileCommand=HotCodeHeap,compiler/codecache/hotcodeheap/TestHotCodeHeapCompileDirective.callable
 *                   compiler.codecache.hotcodeheap.TestHotCodeHeapCompileDirective02
 */

package compiler.codecache.hotcodeheap;

import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.NMethod;

import static jdk.test.lib.Asserts.assertEQ;
import static jdk.test.lib.Asserts.assertNE;

import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_NONE;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE;

public class TestHotCodeHeapCompileDirective02 extends TestHotCodeHeapCompileDirective {

    // Compile a method by C1.
    // Mark the method as hot with compiler directive UseState.
    // Expect: the method is not recompiled and is not in the HotCodeHeap.
    // Compile the method by C2.
    // Expect: the method is recompiled and is not in the HotCodeHeap.
    public static class Test01 extends Test {

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "$method",
                        c2: {
                            UseState: 1
                        }
                    }
                ]""";
        }

        @Override
        void runTest() throws Exception {
            if (!(Boolean)WB.getVMFlag("TieredCompilation")) {
                System.out.println("Test01: Test requires TieredCompilation enabled. Skipping.");
                return;
            }

            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE);
            addDirectivesWithRefreshAndLevelCheck(COMP_LEVEL_SIMPLE);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
        }
    }

    // Compile a method by C2.
    // Expect: the method is not in HotCodeHeap.
    // Mark the method with compiler directive UseState as hot.
    // Expect: the method is recompiled and is not in the HotCodeHeap.
    public static class Test02 extends Test {

       @Override
       String getDirectives() {
           return """
               [
                   {
                       match: "$method",
                       c2: {
                           UseState: 1
                       }
                   }
               ]""";
       }

       @Override
       void runTest() throws Exception {
           compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
           assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
           var compile_id = NMethod.get(method, false).compile_id;

           addDirectivesWithRefreshAndLevelCheck(COMP_LEVEL_FULL_OPTIMIZATION);
           assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
           assertNE(compile_id, NMethod.get(method, false).compile_id);
        }
    }

    // Compile a method marked with compiler directive UseState as hot by C2.
    // Expect: the method is not in HotCodeHeap.
    // Mark the method with compiler directive UseState again as hot.
    // Expect: the method is recompiled and is not in the HotCodeHeap.
    public static class Test03 extends Test {

       @Override
       String getInitialDirectives() {
           return """
               [
                   {
                       match: "$method",
                       c2: {
                           UseState: 1
                       }
                   }
               ]""";
       }

       @Override
       String getDirectives() {
           return """
               [
                   {
                       match: "$method",
                       c2: {
                           UseState: 1
                       }
                   }
               ]""";
       }

       @Override
       void runTest() throws Exception {
           compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
           var compile_id = NMethod.get(method, false).compile_id;
           assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);

           addDirectivesWithRefreshAndLevelCheck(COMP_LEVEL_FULL_OPTIMIZATION);
           assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
           assertNE(compile_id, NMethod.get(method, false).compile_id);
       }
    }

    // Compile a method marked with compiler directive UseState as hot by C2.
    // Mark the method with compiler directive UseState as cold.
    // Expect: the method is recompiled and is not in HotCodeHeap.
    // Clear directives
    // Compile the method with C2 again.
    // Expect: the method is recompiled and is not in the HotCodeHeap.
    public static class Test04 extends Test {

        @Override
        String getInitialDirectives() {
            return """
                [
                    {
                        match: "$method",
                        c2: {
                            UseState: 1
                        }
                    }
                ]""";
        }

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "$method",
                        c2: {
                            UseState: -1
                        }
                    }
                ]""";
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
            var nmethod = NMethod.get(method, false);
            assertNE(nmethod.code_blob_type, BlobType.MethodHot);
            var compile_id = nmethod.compile_id;

            addDirectivesWithRefreshAndLevelCheck(COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
            assertNE(compile_id, NMethod.get(method, false).compile_id);
            compile_id = NMethod.get(method, false).compile_id;
            clearDirectives();
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
            assertEQ(compile_id, NMethod.get(method, false).compile_id);
        }
    }

    // Compile a method by C2.
    // Expect: the method is not in HotCodeHeap.
    // Mark the method with compiler directive UseState as hot.
    // Deoptimize the method.
    // Compile the method by C2 again.
    // Expect: the method is recompiled and is not in the HotCodeHeap.
    public static class Test05 extends Test {

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "$method",
                        c2: {
                            UseState: 1
                        }
                    }
                ]""";
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
            var compile_id = NMethod.get(method, false).compile_id;

            addDirectivesWithRefreshAndLevelCheck(COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
            assertNE(compile_id, NMethod.get(method, false).compile_id);
            WB.deoptimizeMethod(method);
            checkCompilationLevel(method, COMP_LEVEL_NONE);
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
        }
    }

    // Compile a method by C2.
    // Expect: the method is not in HotCodeHeap.
    // Mark the method with compiler directive UseState as hot.
    // Clear directives.
    // Deoptimize the method.
    // Compile the method with C2 again.
    // Expect: the method is recompiled and is not in the HotCodeHeap.
    public static class Test06 extends Test {

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "$method",
                        c2: {
                            UseState: 1
                        }
                    }
                ]""";
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
            var compile_id = NMethod.get(method, false).compile_id;

            addDirectivesWithRefreshAndLevelCheck(COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
            assertNE(compile_id, NMethod.get(method, false).compile_id);
            clearDirectives();
            WB.deoptimizeMethod(method);
            checkCompilationLevel(method, COMP_LEVEL_NONE);
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
        }
    }

    // Compile a method marked with compiler directive UseState as cold by C2.
    // Expect: the method is compiled and not in HotCodeHeap.
    // Mark the method compiler directive UseState as hot and compile by C2.
    // Expect: the method is compiled and is not in the HotCodeHeap.
    public static class Test07 extends Test {

        @Override
        String getInitialDirectives() {
            return """
                [
                    {
                        match: "$method",
                        c2: {
                            UseState: -1
                        }
                    }
                ]""";
        }

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "$method",
                        c2: {
                            UseState: 1
                        }
                    }
                ]""";
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
            var compile_id = NMethod.get(method, false).compile_id;

            addDirectivesWithRefreshAndLevelCheck(COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
            assertNE(compile_id, NMethod.get(method, false).compile_id);
        }
    }

    // Compile a method marked with compiler directive as hot by C2.
    // Expect: the method is compiled and is not in the HotCodeHeap.
    // Mark the method with compiler directive UseState as cold.
    // Expect: the method is recompiled.
    public static class Test08 extends Test {

        @Override
        String getInitialDirectives() {
            return """
                [
                    {
                        match: "$method",
                        c2: {
                            UseState: 1
                        }
                    }
                ]""";
        }

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "$method",
                        c2: {
                            UseState: -1
                        }
                    }
                ]""";
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
            var nmethod = NMethod.get(method, false);
            assertNE(nmethod.code_blob_type, BlobType.MethodHot);
            var compile_id = nmethod.compile_id;

            addDirectivesWithRefreshAndLevelCheck(COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
            assertNE(compile_id, NMethod.get(method, false).compile_id);
        }
    }

    public static void main(String[] args) throws Exception {
       new Test01().run();
       new Test02().run();
       new Test03().run();
       new Test04().run();
       new Test05().run();
       new Test06().run();
       new Test07().run();
       new Test08().run();
    }
}
