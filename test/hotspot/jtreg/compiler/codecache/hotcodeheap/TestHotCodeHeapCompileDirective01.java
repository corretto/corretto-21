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
 * @test TestHotCodeHeapCompileDirective01
 * @summary Test Java methods marked hot or cold with compiler directive UseState are got in or out of HotCodeHeap
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xlog:codecache=trace -XX:+TieredCompilation
 *                   -XX:+ProfileInterpreter -XX:+ProfileTraps -XX:HotCodeHeapSize=4M
 *                   compiler.codecache.hotcodeheap.TestHotCodeHeapCompileDirective01
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xlog:codecache=trace -XX:-TieredCompilation
 *                   -XX:+ProfileInterpreter -XX:+ProfileTraps -XX:HotCodeHeapSize=4M
 *                   compiler.codecache.hotcodeheap.TestHotCodeHeapCompileDirective01
 */

package compiler.codecache.hotcodeheap;

import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.NMethod;

import static jdk.test.lib.Asserts.assertEQ;
import static jdk.test.lib.Asserts.assertNE;

import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_NONE;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE;

public class TestHotCodeHeapCompileDirective01 extends TestHotCodeHeapCompileDirective {

    // Compile a method by C1.
    // Mark the method as hot with compiler directive UseState.
    // Expect: the method is not recompiled and is not in the HotCodeHeap.
    // Compile the method by C2.
    // Expect: the method is recompiled and is in the HotCodeHeap.
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
            assertEQ(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
        }
    }

    // Compile a method by C2.
    // Expect: the method is not in HotCodeHeap.
    // Mark the method with compiler directive UseState as hot.
    // Expect: the method is recompiled and is in the HotCodeHeap.
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
           assertEQ(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
           assertNE(compile_id, NMethod.get(method, false).compile_id);
        }
    }

    // Compile a method marked with compiler directive UseState as hot by C2.
    // Expect: the method is in HotCodeHeap.
    // Mark the method with compiler directive UseState again as hot.
    // Expect: the method is not recompiled and is in the HotCodeHeap.
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
           assertEQ(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);

           addDirectivesWithRefreshAndLevelCheck(COMP_LEVEL_FULL_OPTIMIZATION);
           assertEQ(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
           assertEQ(compile_id, NMethod.get(method, false).compile_id);
       }
    }

    // Compile a method marked with compiler directive UseState as hot by C2.
    // Mark the method with compiler directive UseState as cold.
    // Expect: the method is not recompiled and no code available.
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
            assertEQ(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);

            addDirectivesWithRefreshAndLevelCheck(COMP_LEVEL_NONE);
            checkCompilationLevel(method, COMP_LEVEL_NONE);
            clearDirectives();
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
        }
    }

    // Compile a method by C2.
    // Expect: the method is not in HotCodeHeap.
    // Mark the method with compiler directive UseState as hot.
    // Deoptimize the method.
    // Compile the method by C2 again.
    // Expect: the method is recompiled and is in the HotCodeHeap.
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

            addDirectivesWithRefreshAndLevelCheck(COMP_LEVEL_FULL_OPTIMIZATION);
            assertEQ(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
            WB.deoptimizeMethod(method);
            checkCompilationLevel(method, COMP_LEVEL_NONE);
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
            assertEQ(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
        }
    }

    // Compile a method by C2.
    // Expect: the method is not in HotCodeHeap.
    // Mark the method with compiler directive UseState as hot.
    // Clear directives.
    // Deoptimize the method.
    // Compile the method with C2 again.
    // Expect: the method is recompiled and is in the HotCodeHeap.
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

            addDirectivesWithRefreshAndLevelCheck(COMP_LEVEL_FULL_OPTIMIZATION);
            assertEQ(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
            clearDirectives();
            WB.deoptimizeMethod(method);
            checkCompilationLevel(method, COMP_LEVEL_NONE);
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
            assertEQ(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
        }
    }

    // Compile a method marked with compiler directive UseState as cold by C2.
    // Expect: the method is compiled and not in HotCodeHeap.
    // Mark the method compiler directive UseState as hot and compile by C2.
    // Expect: the method is compiled and is in the HotCodeHeap.
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
            assertEQ(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
            assertNE(compile_id, NMethod.get(method, false).compile_id);
        }
    }

    // Compile a method marked with compiler directive as hot by C2.
    // Expect: the method is compiled and is in the HotCodeHeap.
    // Mark the method with compiler directive UseState as cold.
    // Expect: the method is not recompiled and no code available.
    // Compile the method by C2 again.
    // Expect: the method is recompiled and is not in the HotCodeHeap.
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
            assertEQ(nmethod.code_blob_type, BlobType.MethodHot);

            addDirectivesWithRefreshAndLevelCheck(COMP_LEVEL_NONE);
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
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
