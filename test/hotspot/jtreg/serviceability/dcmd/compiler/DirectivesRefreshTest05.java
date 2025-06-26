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
 * @test DirectivesRefreshTest05
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
 *                   serviceability.dcmd.compiler.DirectivesRefreshTest05
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+BackgroundCompilation -XX:+TieredCompilation -XX:CICompilerCount=2
 *                   -Xlog:codecache=trace
 *                   serviceability.dcmd.compiler.DirectivesRefreshTest05
 *
  * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-BackgroundCompilation -XX:+TieredCompilation -XX:CICompilerCount=2
 *                   -Xlog:codecache=trace
 *                   serviceability.dcmd.compiler.DirectivesRefreshTest05
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
import static jdk.test.lib.Asserts.assertNE;
import static jdk.test.lib.Asserts.assertFalse;

import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_NONE;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE;

public class DirectivesRefreshTest05 {

    static final int INVOCATION_ENTRY_BCI = -1;

    static WhiteBox wb = WhiteBox.getWhiteBox();
    static Random random = new Random();

    static void checkCompilationLevel(Method method, int level, boolean isOSR) {
        assertEQ(level, wb.getMethodCompilationLevel(method, isOSR), "Compilation level");
    }

    static void checkCompilationLevel(Method method, int level) {
        checkCompilationLevel(method, level, false);
    }

    static void checkOsrCompilationLevel(Method method, int level) {
        checkCompilationLevel(method, level, true);
    }

    static void clearDirectives() throws Exception {
        new JMXExecutor().execute("Compiler.directives_clear");
    }

    static void listDirectives() throws Exception {
        new JMXExecutor().execute("Compiler.directives_print");
    }

    static void compileMethod(Method method, int level) {
        compileMethod(method, level, INVOCATION_ENTRY_BCI);
    }

    static void compileMethod(Method method, int level, int entry_bci) {
        wb.enqueueMethodForCompilation(method, level, entry_bci);
        while (wb.isMethodQueuedForCompilation(method)) {
            Thread.onSpinWait();
        }
    }

    static void compileMethodWithCheck(Method method, int level, int entry_bci) {
        compileMethod(method, level, entry_bci);
        checkCompilationLevel(method, level, entry_bci != INVOCATION_ENTRY_BCI /*isOSR*/);
    }

    static void compileMethodWithCheck(Method method, int level) {
        compileMethodWithCheck(method, level, INVOCATION_ENTRY_BCI);
    }

    static void deoptimizeMethod(Method method) {
        wb.deoptimizeMethod(method);
    }

    static void deoptimizeOsrMethod(Method method) {
        wb.deoptimizeMethod(method, true /*isOSR*/);
    }

    static int calc() {
        int result = 0;
        for (int i = 0; i < 100; i++) {
            result += random.nextInt(100);
        }
        return result;
    }

    static int callable() {
        int result = 0;
        while (!stop) {
            result += calc();
        }
        return result;
    }

    static int osrBci;
    static volatile boolean stop;
    static Method method;

    static void findOSRBci() throws Exception {
        method = DirectivesRefreshTest05.class.getDeclaredMethod("callable");
        stop = false;
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    callable();
                } catch (Exception e) {}
            }
        });
        thread.start();
        while (thread.isAlive() && !wb.isMethodCompiled(method, true)) {
            Thread.onSpinWait();
        }
        stop = true;
        osrBci = wb.getMethodEntryBci(method);
        assertNE(osrBci, INVOCATION_ENTRY_BCI, "Method should be OSR compiled");
    }

    static abstract class AddRefreshTest {
        static final boolean KEEP_FILES = false;

        Path directivesPath = null;

        abstract String getDirectives();
        abstract void runTest() throws Exception;

        String getInitialDirectives() {
            return null;
        }

        void writeDirectives() throws Exception {
            directivesPath = Files.createTempFile("DirectivesRefreshTest05-" + this.getClass().getSimpleName(), "");
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
                clearDirectives();
                deoptimizeMethod(method);
                deoptimizeOsrMethod(method);
                checkCompilationLevel(method, COMP_LEVEL_NONE);
                checkOsrCompilationLevel(method, COMP_LEVEL_NONE);
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
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE, osrBci);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkOsrCompilationLevel(method, COMP_LEVEL_SIMPLE);

            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
        }
    }

    // We add new directives for callable() but we have them disabled.
    // We should not recompile the method.
    public static class AddRefreshTest02 extends AddRefreshTest {

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
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE, osrBci);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkCompilationLevel(method, COMP_LEVEL_SIMPLE);
            checkOsrCompilationLevel(method, COMP_LEVEL_SIMPLE);

            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
        }
    }

    // We add new directives for callable(). C1 directives are disabled.
    // If we have `callable` C1 compiled, it should not be recompiled.
    public static class AddRefreshTest03 extends AddRefreshTest {

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
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE, osrBci);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkCompilationLevel(method, COMP_LEVEL_SIMPLE);
            checkOsrCompilationLevel(method, COMP_LEVEL_SIMPLE);

            compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION);
            checkCompilationLevel(method, COMP_LEVEL_SIMPLE);

            compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            checkOsrCompilationLevel(method, COMP_LEVEL_SIMPLE);

            deoptimizeMethod(method);
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE);

            deoptimizeOsrMethod(method);
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE, osrBci);
        }
    }

    // We add new directives for callable(). C1 directives are disabled.
    // If we have `callable` C1 compiled, it should not be recompiled.
    public static class AddRefreshTest04 extends AddRefreshTest {

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
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE, osrBci);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkOsrCompilationLevel(method, COMP_LEVEL_SIMPLE);

            compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            checkOsrCompilationLevel(method, COMP_LEVEL_SIMPLE);

            deoptimizeOsrMethod(method);
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE, osrBci);
        }
    }

    // We add a new C2 directive for callable().
    // If we have `callable` only C1 compiled, it should not be recompiled.
    public static class AddRefreshTest05 extends AddRefreshTest {

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
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE, osrBci);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkOsrCompilationLevel(method, COMP_LEVEL_SIMPLE);

            compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            checkOsrCompilationLevel(method, COMP_LEVEL_SIMPLE);

            deoptimizeOsrMethod(method);
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE, osrBci);
        }
    }

    // We add a new C2 directive for callable().
    // If we have `callable` C2 compiled, it should be recompiled.
    public static class AddRefreshTest06 extends AddRefreshTest {

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
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkOsrCompilationLevel(method, COMP_LEVEL_NONE);

            compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            checkOsrCompilationLevel(method, COMP_LEVEL_NONE);

            deoptimizeOsrMethod(method);
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE, osrBci);
        }
    }

    // We exclude callable() from C1/C2 compilations.
    // If we have `callable` C1 compiled, it should be made not entrant.
    public static class AddRefreshTest07 extends AddRefreshTest {

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
            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE, osrBci);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkOsrCompilationLevel(method, COMP_LEVEL_NONE);

            compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            checkOsrCompilationLevel(method, COMP_LEVEL_NONE);

            deoptimizeOsrMethod(method);
            compileMethod(method, COMP_LEVEL_SIMPLE, osrBci);
            checkOsrCompilationLevel(method, COMP_LEVEL_NONE);
        }
    }

    // We exclude callable() from C1/C2 compilations.
    // If we have `callable` C2 compiled, it should be made not entrant.
    public static class AddRefreshTest08 extends AddRefreshTest {

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
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkOsrCompilationLevel(method, COMP_LEVEL_NONE);

            compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            checkOsrCompilationLevel(method, COMP_LEVEL_NONE);

            deoptimizeOsrMethod(method);
            compileMethod(method, COMP_LEVEL_SIMPLE, osrBci);
            checkOsrCompilationLevel(method, COMP_LEVEL_NONE);
        }
    }

    // We exclude callable() from compilation.
    // If we have `callable` C2 compiled, it should be made not entrant.
    public static class AddRefreshTest09 extends AddRefreshTest {

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
            compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("1 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method), "Method should not be queued for recompilation");
            checkOsrCompilationLevel(method, COMP_LEVEL_NONE);

            compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            checkOsrCompilationLevel(method, COMP_LEVEL_NONE);

            compileMethodWithCheck(method, COMP_LEVEL_SIMPLE, osrBci);
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
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest05$AddRefreshTest10::*",
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
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest05$AddRefreshTest10::callable01",
                        c2: {
                            Exclude: true
                        },
                    },
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest05$AddRefreshTest10::callable02",
                        c2: {
                            Exclude: true
                        },
                    },
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest05$AddRefreshTest10::callable03",
                        c2: {
                            Exclude: true
                        },
                    }
                ]""";
        }

        static int callable01() {
            int result = 0;
            while (!stop) {
                result += calc();
            }
            return result;
        }

        static int callable02() {
            int result = 0;
            while (!stop) {
                result += calc();
            }
            return result;
        }

        static int callable03() {
            int result = 0;
            while (!stop) {
                result += calc();
            }
            return result;
        }

        Method method1;
        Method method2;
        Method method3;

        AddRefreshTest10() throws Exception {
            super();
            method1 = this.getClass().getDeclaredMethod("callable01");
            method2 = this.getClass().getDeclaredMethod("callable02");
            method3 = this.getClass().getDeclaredMethod("callable03");
            deoptimizeMethod(method1);
            deoptimizeMethod(method2);
            deoptimizeMethod(method3);
            deoptimizeOsrMethod(method1);
            deoptimizeOsrMethod(method2);
            deoptimizeOsrMethod(method3);
            checkCompilationLevel(method1, COMP_LEVEL_NONE);
            checkOsrCompilationLevel(method1, COMP_LEVEL_NONE);
            checkCompilationLevel(method2, COMP_LEVEL_NONE);
            checkOsrCompilationLevel(method2, COMP_LEVEL_NONE);
            checkCompilationLevel(method3, COMP_LEVEL_NONE);
            checkOsrCompilationLevel(method3, COMP_LEVEL_NONE);
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method1, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            compileMethodWithCheck(method2, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            compileMethodWithCheck(method3, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("3 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method1), "Method should not be queued for recompilation");
            assertFalse(wb.isMethodQueuedForCompilation(method2), "Method should not be queued for recompilation");
            assertFalse(wb.isMethodQueuedForCompilation(method3), "Method should not be queued for recompilation");
            checkOsrCompilationLevel(method1, COMP_LEVEL_NONE);
            checkOsrCompilationLevel(method2, COMP_LEVEL_NONE);
            checkOsrCompilationLevel(method3, COMP_LEVEL_NONE);

            compileMethod(method1, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            checkOsrCompilationLevel(method1, COMP_LEVEL_NONE);
            compileMethod(method2, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            checkOsrCompilationLevel(method2, COMP_LEVEL_NONE);
            compileMethod(method3, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            checkOsrCompilationLevel(method3, COMP_LEVEL_NONE);

            compileMethodWithCheck(method1, COMP_LEVEL_SIMPLE, osrBci);
            compileMethodWithCheck(method2, COMP_LEVEL_SIMPLE, osrBci);
            compileMethodWithCheck(method3, COMP_LEVEL_SIMPLE, osrBci);
        }
    }

    // We add new C2 directives for callable01(), callable02(), callable03().
    // We check they should be recompiled.
    public static class AddRefreshTest11 extends AddRefreshTest {

        @Override
        String getDirectives() {
            return """
                [
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest05$AddRefreshTest11::callable01",
                        c2: {
                            Exclude: true
                        },
                    },
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest05$AddRefreshTest11::callable02",
                        c2: {
                            Exclude: true
                        },
                    },
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest05$AddRefreshTest11::callable03",
                        c2: {
                            Exclude: true
                        },
                    }
                ]""";
        }

        static int callable01() {
            int result = 0;
            while (!stop) {
                result += calc();
            }
            return result;
        }

        static int callable02() {
            int result = 0;
            while (!stop) {
                result += calc();
            }
            return result;
        }

        static int callable03() {
            int result = 0;
            while (!stop) {
                result += calc();
            }
            return result;
        }

        Method method1;
        Method method2;
        Method method3;

        AddRefreshTest11() throws Exception {
            super();
            method1 = this.getClass().getDeclaredMethod("callable01");
            method2 = this.getClass().getDeclaredMethod("callable02");
            method3 = this.getClass().getDeclaredMethod("callable03");
            deoptimizeMethod(method1);
            deoptimizeMethod(method2);
            deoptimizeMethod(method3);
            deoptimizeOsrMethod(method1);
            deoptimizeOsrMethod(method2);
            deoptimizeOsrMethod(method3);
            checkCompilationLevel(method1, COMP_LEVEL_NONE);
            checkOsrCompilationLevel(method1, COMP_LEVEL_NONE);
            checkCompilationLevel(method2, COMP_LEVEL_NONE);
            checkOsrCompilationLevel(method2, COMP_LEVEL_NONE);
            checkCompilationLevel(method3, COMP_LEVEL_NONE);
            checkOsrCompilationLevel(method3, COMP_LEVEL_NONE);
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method1, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            compileMethodWithCheck(method2, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            compileMethodWithCheck(method3, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("3 compiler directives added");
            assertFalse(wb.isMethodQueuedForCompilation(method1), "Method should not be queued for recompilation");
            assertFalse(wb.isMethodQueuedForCompilation(method2), "Method should not be queued for recompilation");
            assertFalse(wb.isMethodQueuedForCompilation(method3), "Method should not be queued for recompilation");
            checkOsrCompilationLevel(method1, COMP_LEVEL_NONE);
            checkOsrCompilationLevel(method2, COMP_LEVEL_NONE);
            checkOsrCompilationLevel(method3, COMP_LEVEL_NONE);

            compileMethod(method1, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            checkCompilationLevel(method1, COMP_LEVEL_NONE);
            compileMethod(method2, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            checkCompilationLevel(method2, COMP_LEVEL_NONE);
            compileMethod(method3, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            checkCompilationLevel(method3, COMP_LEVEL_NONE);

            compileMethodWithCheck(method1, COMP_LEVEL_SIMPLE, osrBci);
            compileMethodWithCheck(method2, COMP_LEVEL_SIMPLE, osrBci);
            compileMethodWithCheck(method3, COMP_LEVEL_SIMPLE, osrBci);
        }
    }

    // We add new C2 directives for callable01(), callable02(), callable03().
    // We check they should be recompiled.
    public static class AddRefreshTest12 extends AddRefreshTest {

        @Override
        String getInitialDirectives() {
            return """
                [
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest05$AddRefreshTest12::callable01",
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
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest05$AddRefreshTest12::callable02",
                        c2: {
                            Exclude: true
                        },
                    },
                    {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest05$AddRefreshTest12::callable03",
                        c2: {
                            Exclude: true
                        },
                    }
                ]""";
        }

        static int callable01() {
            int result = 0;
            while (!stop) {
                result += calc();
            }
            return result;
        }

        static int callable02() {
            int result = 0;
            while (!stop) {
                result += calc();
            }
            return result;
        }

        static int callable03() {
            int result = 0;
            while (!stop) {
                result += calc();
            }
            return result;
        }

        Method method1;
        Method method2;
        Method method3;

        AddRefreshTest12() throws Exception {
            super();
            method1 = this.getClass().getDeclaredMethod("callable01");
            method2 = this.getClass().getDeclaredMethod("callable02");
            method3 = this.getClass().getDeclaredMethod("callable03");
            deoptimizeMethod(method1);
            deoptimizeMethod(method2);
            deoptimizeMethod(method3);
            deoptimizeOsrMethod(method1);
            deoptimizeOsrMethod(method2);
            deoptimizeOsrMethod(method3);
            checkCompilationLevel(method1, COMP_LEVEL_NONE);
            checkOsrCompilationLevel(method1, COMP_LEVEL_NONE);
            checkCompilationLevel(method2, COMP_LEVEL_NONE);
            checkOsrCompilationLevel(method2, COMP_LEVEL_NONE);
            checkCompilationLevel(method3, COMP_LEVEL_NONE);
            checkOsrCompilationLevel(method3, COMP_LEVEL_NONE);
        }

        @Override
        void runTest() throws Exception {
            compileMethodWithCheck(method1, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            compileMethodWithCheck(method2, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            compileMethodWithCheck(method3, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);

            addDirectivesWithRefresh().stderrShouldBeEmpty().shouldContain("2 compiler directives added");

            assertFalse(wb.isMethodQueuedForCompilation(method1), "Method should not be queued for recompilation");
            assertFalse(wb.isMethodQueuedForCompilation(method2), "Method should not be queued for recompilation");
            assertFalse(wb.isMethodQueuedForCompilation(method3), "Method should not be queued for recompilation");
            checkOsrCompilationLevel(method1, COMP_LEVEL_FULL_OPTIMIZATION);
            checkOsrCompilationLevel(method2, COMP_LEVEL_NONE);
            checkOsrCompilationLevel(method3, COMP_LEVEL_NONE);

            compileMethod(method2, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            checkOsrCompilationLevel(method2, COMP_LEVEL_NONE);
            compileMethod(method3, COMP_LEVEL_FULL_OPTIMIZATION, osrBci);
            checkOsrCompilationLevel(method3, COMP_LEVEL_NONE);

            deoptimizeOsrMethod(method1);
            deoptimizeOsrMethod(method2);
            deoptimizeOsrMethod(method3);
            compileMethodWithCheck(method1, COMP_LEVEL_SIMPLE, osrBci);
            compileMethodWithCheck(method2, COMP_LEVEL_SIMPLE, osrBci);
            compileMethodWithCheck(method3, COMP_LEVEL_SIMPLE, osrBci);
        }
    }

    public static void main(String[] args) throws Exception {
        findOSRBci();

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
        new AddRefreshTest11().run();
        new AddRefreshTest12().run();
    }
}
