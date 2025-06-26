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
 * @test TestHotCodeHeapCompileDirective04
 * @summary Test Java methods marked cold with compiler directive UseState are got out of
 *          HotCodeHeap. With RecompileColdNMethods enabled, the method marked as cold is recompiled.
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xlog:codecache=trace -XX:+TieredCompilation -XX:+RecompileColdNMethods
 *                   -XX:+ProfileInterpreter -XX:+ProfileTraps -XX:HotCodeHeapSize=4M
 *                   compiler.codecache.hotcodeheap.TestHotCodeHeapCompileDirective04
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xlog:codecache=trace -XX:-TieredCompilation -XX:+RecompileColdNMethods
 *                   -XX:+ProfileInterpreter -XX:+ProfileTraps -XX:HotCodeHeapSize=4M
 *                   compiler.codecache.hotcodeheap.TestHotCodeHeapCompileDirective04
 */

package compiler.codecache.hotcodeheap;

import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.NMethod;

import static jdk.test.lib.Asserts.assertEQ;
import static jdk.test.lib.Asserts.assertNE;

import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_NONE;

public class TestHotCodeHeapCompileDirective04 extends TestHotCodeHeapCompileDirective {

    // Compile a method marked as hot by C2.
    // Mark the method as cold.
    // Expect: the method is recompiled and not in HotCodeHeap.
    public static class Test01 extends Test {

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

            addDirectivesWithRefreshAndLevelCheck(COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
        }
    }

    // Compile a method marked as cold by C2.
    // Mark the method as cold.
    // Expect: the method is recompiled and not in HotCodeHeap.
    public static class Test02 extends Test {

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

            addDirectivesWithRefreshAndLevelCheck(COMP_LEVEL_FULL_OPTIMIZATION);
            assertNE(NMethod.get(method, false).code_blob_type, BlobType.MethodHot);
        }
    }

    public static void main(String[] args) throws Exception {
       new Test01().run();
       new Test02().run();
    }
}
