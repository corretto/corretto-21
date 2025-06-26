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
 * @test DirectivesRefreshTest03
 * @summary Test Java methods get recompiled after compiler directives changes by a diagnostic command
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xlog:codecache=trace
 *                   serviceability.dcmd.compiler.DirectivesRefreshTest03
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+BackgroundCompilation -XX:+TieredCompilation -XX:CICompilerCount=2
 *                   -Xlog:codecache=trace
 *                   serviceability.dcmd.compiler.DirectivesRefreshTest03
 *
  * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-BackgroundCompilation -XX:+TieredCompilation -XX:CICompilerCount=2
 *                   -Xlog:codecache=trace
 *                   serviceability.dcmd.compiler.DirectivesRefreshTest03
 */

package serviceability.dcmd.compiler;

import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.WhiteBox;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import static jdk.test.lib.Asserts.assertEQ;
import static jdk.test.lib.Asserts.assertFalse;

import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_NONE;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE;

public class DirectivesRefreshTest03 {

    static WhiteBox wb = WhiteBox.getWhiteBox();
    static Random random = new Random();

    static void checkCompilationLevel(Method method, int level) {
        assertEQ(level, wb.getMethodCompilationLevel(method), "Compilation level");
    }

    static void clearDirectives() throws Exception {
        new JMXExecutor().execute("Compiler.directives_clear");
    }

    static void listDirectives() throws Exception {
        new JMXExecutor().execute("Compiler.directives_print");
    }

    static void compileMethod(Method method, int level) {
        wb.enqueueMethodForCompilation(method, level);
        while (wb.isMethodQueuedForCompilation(method)) {
            Thread.onSpinWait();
        }
    }

    static void compileMethodWithCheck(Method method, int level) {
        compileMethod(method, level);
        checkCompilationLevel(method, level);
    }

    static int callable() {
        int result = 0;
        for (int i = 0; i < 100; i++) {
            result += random.nextInt(100);
        }
        return result;
    }

    static abstract class AddRefreshTest {
        static final boolean KEEP_FILES = false;

        Path directivesPath = null;
        Method method;

        abstract String getDirectives();
        abstract void runTest() throws Exception;

        String getInitialDirectives() {
            return null;
        }

        void writeDirectives() throws Exception {
            directivesPath = Files.createTempFile("DirectivesRefreshTest04-" + this.getClass().getSimpleName(), "");
            Files.writeString(directivesPath, getDirectives().replace("$method", method.getDeclaringClass().getName() + "::" + method.getName()));
        }

        void addInitialDirectives() throws Exception {
            String dirs = getInitialDirectives();
            if (dirs != null) {
                dirs = dirs.replace("$method", method.getDeclaringClass().getName() + "::" + method.getName());
                wb.addCompilerDirective(dirs);
            }
        }

        AddRefreshTest() {
            try {
                method = DirectivesRefreshTest04.class.getDeclaredMethod("callable");

                clearDirectives();
                wb.deoptimizeMethod(method);
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

        OutputAnalyzer addDirectivesWithRefresh() throws Exception {
            return new JMXExecutor().execute("Compiler.directives_add -r " + directivesPath.toString());
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
            } catch (Exception e) {
                listDirectives();
                throw e;
            } finally {
                cleanup();
            }
        }
    }

    // We add new directives for callable() but we have them disabled.
    // We should not recompile the method.
    public static class AddRefreshTest01 extends AddRefreshTest {

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "$method",
                        c2: {
                            Exclude: false
                        },
                        c1: {
                            Exclude: false
                        },
                        Enable: false
                    }
                ]""";
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkCompilationLevel(method, COMP_LEVEL_SIMPLE);

            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
        }
    }

    // We add new directives for callable(). C1 directives are disabled.
    // If we have `callable` C1 compiled, it should not be recompiled.
    public static class AddRefreshTest02 extends AddRefreshTest {

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "$method",
                        c1: {
                            Enable: false,
                            Exclude: true
                        },
                        c2: {
                            Exclude: true
                        }
                    }
                ]""";
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkCompilationLevel(method, COMP_LEVEL_SIMPLE);

            compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method, COMP_LEVEL_SIMPLE);

            wb.deoptimizeMethod(method);
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE);
        }
    }

    // We add a new C2 directive for callable().
    // If we have `callable` only C1 compiled, it should not be recompiled.
    public static class AddRefreshTest03 extends AddRefreshTest {

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "$method",
                        c2: {
                            Exclude: true
                        }
                    }
                ]""";
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkCompilationLevel(method, COMP_LEVEL_SIMPLE);

            compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method, COMP_LEVEL_SIMPLE);

            wb.deoptimizeMethod(method);
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE);
        }
    }

    // We add a new C2 directive for callable().
    // If we have `callable` C2 compiled, it should be recompiled.
    public static class AddRefreshTest04 extends AddRefreshTest {

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "$method",
                        c2: {
                            Exclude: true
                        }
                    }
                ]""";
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkCompilationLevel(method, COMP_LEVEL_NONE);

            compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method, COMP_LEVEL_NONE);

            wb.deoptimizeMethod(method);
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE);
        }
    }

    // We exclude callable() from compilation.
    // If we have `callable` C1 compiled, it should be made not entrant.
    public static class AddRefreshTest05 extends AddRefreshTest {

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "$method",
                        Exclude: true
                    }
                ]""";
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkCompilationLevel(method, COMP_LEVEL_NONE);

            compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method, COMP_LEVEL_NONE);

            wb.deoptimizeMethod(method);
            compileMethod(method, COMP_LEVEL_SIMPLE);
            checkCompilationLevel(method, COMP_LEVEL_NONE);
        }
    }

    // We exclude callable() from compilation.
    // If we have `callable` C2 compiled, it should be made not entrant.
    public static class AddRefreshTest06 extends AddRefreshTest {

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "$method",
                        Exclude: true
                    }
                ]""";
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkCompilationLevel(method, COMP_LEVEL_NONE);

            compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method, COMP_LEVEL_NONE);

            wb.deoptimizeMethod(method);
            compileMethod(method, COMP_LEVEL_SIMPLE);
            checkCompilationLevel(method, COMP_LEVEL_NONE);
        }
    }

    // We exclude callable() from compilation.
    // If we have `callable` C2 compiled, it should be made not entrant.
    public static class AddRefreshTest07 extends AddRefreshTest {

        @Override
        String getInitialDirectives() {
            return """
                [
                    {
                        match: "$method",
                        c2: {
                            Exclude: false
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
                            Exclude: true
                        }
                    }
                ]""";
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkCompilationLevel(method, COMP_LEVEL_NONE);

            compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method, COMP_LEVEL_NONE);

            wb.deoptimizeMethod(method);
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE);
        }
    }

    // We add new C2 directives for callable01(), callable02(), callable03().
    // We check they should be recompiled.
    public static class AddRefreshTest08 extends AddRefreshTest {

        @Override
        String getInitialDirectives() {
            return """
                [
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest03$AddRefreshTest08::*",
                        c2: {
                            Exclude: false
                        }
                    }
                ]""";
        }

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest03$AddRefreshTest08::callable01",
                        c2: {
                            Exclude: true
                        },
                    },
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest03$AddRefreshTest08::callable02",
                        c2: {
                            Exclude: true
                        },
                    },
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest03$AddRefreshTest08::callable03",
                        c2: {
                            Exclude: true
                        },
                    }
                ]""";
        }

        static int callable01() {
            return DirectivesRefreshTest03.callable();
        }

        static int callable02() {
            return DirectivesRefreshTest03.callable();
        }

        static int callable03() {
            return DirectivesRefreshTest03.callable();
        }

        Method method1;
        Method method2;
        Method method3;

        AddRefreshTest08() throws Exception {
            super();
            method1 = this.getClass().getDeclaredMethod("callable01");
            method2 = this.getClass().getDeclaredMethod("callable02");
            method3 = this.getClass().getDeclaredMethod("callable03");
            wb.deoptimizeMethod(method1);
            wb.deoptimizeMethod(method2);
            wb.deoptimizeMethod(method3);
            checkCompilationLevel(method1, COMP_LEVEL_NONE);
            checkCompilationLevel(method2, COMP_LEVEL_NONE);
            checkCompilationLevel(method3, COMP_LEVEL_NONE);
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method1, COMP_LEVEL_FULL_OPTIMIZATION);
            compileMethodWithCheck(method2, COMP_LEVEL_FULL_OPTIMIZATION);
            compileMethodWithCheck(method3, COMP_LEVEL_FULL_OPTIMIZATION);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("3 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method1), "Method should not be queued for recompilation");
            assertFalse(wb.isMethodQueuedForCompilation(method2), "Method should not be queued for recompilation");
            assertFalse(wb.isMethodQueuedForCompilation(method3), "Method should not be queued for recompilation");
            checkCompilationLevel(method1, COMP_LEVEL_NONE);
            checkCompilationLevel(method2, COMP_LEVEL_NONE);
            checkCompilationLevel(method3, COMP_LEVEL_NONE);

            compileMethod(method1, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method1, COMP_LEVEL_NONE);
            compileMethod(method2, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method2, COMP_LEVEL_NONE);
            compileMethod(method3, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method3, COMP_LEVEL_NONE);

            wb.deoptimizeMethod(method1);
            wb.deoptimizeMethod(method2);
            wb.deoptimizeMethod(method3);
            compileMethodWithCheck(method1, COMP_LEVEL_SIMPLE);
            compileMethodWithCheck(method2, COMP_LEVEL_SIMPLE);
            compileMethodWithCheck(method3, COMP_LEVEL_SIMPLE);
        }
    }

    // We add new C2 directives for callable01(), callable02(), callable03().
    // We check they should be recompiled.
    public static class AddRefreshTest09 extends AddRefreshTest {

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest03$AddRefreshTest09::callable01",
                        c2: {
                            Exclude: true
                        },
                    },
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest03$AddRefreshTest09::callable02",
                        c2: {
                            Exclude: true
                        },
                    },
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest03$AddRefreshTest09::callable03",
                        c2: {
                            Exclude: true
                        },
                    }
                ]""";
        }

        static int callable01() {
            return DirectivesRefreshTest03.callable();
        }

        static int callable02() {
            return DirectivesRefreshTest03.callable();
        }

        static int callable03() {
            return DirectivesRefreshTest03.callable();
        }

        Method method1;
        Method method2;
        Method method3;

        AddRefreshTest09() throws Exception{
            super();
            method1 = this.getClass().getDeclaredMethod("callable01");
            method2 = this.getClass().getDeclaredMethod("callable02");
            method3 = this.getClass().getDeclaredMethod("callable03");
            wb.deoptimizeMethod(method1);
            wb.deoptimizeMethod(method2);
            wb.deoptimizeMethod(method3);
            checkCompilationLevel(method1, COMP_LEVEL_NONE);
            checkCompilationLevel(method2, COMP_LEVEL_NONE);
            checkCompilationLevel(method3, COMP_LEVEL_NONE);
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method1, COMP_LEVEL_FULL_OPTIMIZATION);
            compileMethodWithCheck(method2, COMP_LEVEL_FULL_OPTIMIZATION);
            compileMethodWithCheck(method3, COMP_LEVEL_FULL_OPTIMIZATION);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("3 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method1), "Method should not be queued for recompilation");
            assertFalse(wb.isMethodQueuedForCompilation(method2), "Method should not be queued for recompilation");
            assertFalse(wb.isMethodQueuedForCompilation(method3), "Method should not be queued for recompilation");
            checkCompilationLevel(method1, COMP_LEVEL_NONE);
            checkCompilationLevel(method2, COMP_LEVEL_NONE);
            checkCompilationLevel(method3, COMP_LEVEL_NONE);

            compileMethod(method1, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method1, COMP_LEVEL_NONE);
            compileMethod(method2, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method2, COMP_LEVEL_NONE);
            compileMethod(method3, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method3, COMP_LEVEL_NONE);

            wb.deoptimizeMethod(method1);
            wb.deoptimizeMethod(method2);
            wb.deoptimizeMethod(method3);
            compileMethodWithCheck(method1, COMP_LEVEL_SIMPLE);
            compileMethodWithCheck(method2, COMP_LEVEL_SIMPLE);
            compileMethodWithCheck(method3, COMP_LEVEL_SIMPLE);
        }
    }

    // We add new C2 directives for callable01(), callable02(), callable03().
    // We check they should be recompiled.
    public static class AddRefreshTest10 extends AddRefreshTest {

        @Override
        String getInitialDirectives() {
            return """
                [
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest03$AddRefreshTest10::callable01",
                        c2: {
                            Exclude: false
                        }
                    }
                ]""";
        }

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest03$AddRefreshTest10::callable02",
                        c2: {
                            Exclude: true
                        },
                    },
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest03$AddRefreshTest10::callable03",
                        c2: {
                            Exclude: true
                        },
                    }
                ]""";
        }

        static int callable01() {
            return DirectivesRefreshTest03.callable();
        }

        static int callable02() {
            return DirectivesRefreshTest03.callable();
        }

        static int callable03() {
            return DirectivesRefreshTest03.callable();
        }

        Method method1;
        Method method2;
        Method method3;

        AddRefreshTest10() throws Exception{
            super();
            method1 = this.getClass().getDeclaredMethod("callable01");
            method2 = this.getClass().getDeclaredMethod("callable02");
            method3 = this.getClass().getDeclaredMethod("callable03");
            wb.deoptimizeMethod(method1);
            wb.deoptimizeMethod(method2);
            wb.deoptimizeMethod(method3);
            checkCompilationLevel(method1, COMP_LEVEL_NONE);
            checkCompilationLevel(method2, COMP_LEVEL_NONE);
            checkCompilationLevel(method3, COMP_LEVEL_NONE);
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method1, COMP_LEVEL_FULL_OPTIMIZATION);
            compileMethodWithCheck(method2, COMP_LEVEL_FULL_OPTIMIZATION);
            compileMethodWithCheck(method3, COMP_LEVEL_FULL_OPTIMIZATION);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("2 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method1), "Method should not be queued for recompilation");
            assertFalse(wb.isMethodQueuedForCompilation(method2), "Method should not be queued for recompilation");
            assertFalse(wb.isMethodQueuedForCompilation(method3), "Method should not be queued for recompilation");
            checkCompilationLevel(method1, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method2, COMP_LEVEL_NONE);
            checkCompilationLevel(method3, COMP_LEVEL_NONE);

            compileMethod(method2, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method2, COMP_LEVEL_NONE);
            compileMethod(method3, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method3, COMP_LEVEL_NONE);

            wb.deoptimizeMethod(method1);
            wb.deoptimizeMethod(method2);
            wb.deoptimizeMethod(method3);
            compileMethodWithCheck(method1, COMP_LEVEL_SIMPLE);
            compileMethodWithCheck(method2, COMP_LEVEL_SIMPLE);
            compileMethodWithCheck(method3, COMP_LEVEL_SIMPLE);
        }
    }

    public static void main(String[] args) throws Exception {
        new AddRefreshTest01().run();
        new AddRefreshTest02().run();
        new AddRefreshTest03().run();
        new AddRefreshTest04().run();
        new AddRefreshTest05().run();
        new AddRefreshTest06().run();
        new AddRefreshTest07().run();
        new AddRefreshTest08().run();
        new AddRefreshTest09().run();
        new AddRefreshTest10().run();
    }
}
