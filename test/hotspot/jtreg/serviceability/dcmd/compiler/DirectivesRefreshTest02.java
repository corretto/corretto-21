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
 * @test DirectivesRefreshTest02
 * @summary Test a method is removed from the compile queue after compiler directives changes by diagnostic command
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+BackgroundCompilation -XX:+TieredCompilation -XX:CICompilerCount=2
 *                   -Xlog:codecache=trace -XX:-MethodFlushing
 *                   serviceability.dcmd.compiler.DirectivesRefreshTest02
 */

package serviceability.dcmd.compiler;

import jdk.test.lib.dcmd.JMXExecutor;
import jdk.test.whitebox.WhiteBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.Random;

import static jdk.test.lib.Asserts.assertEQ;

import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_NONE;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;

public class DirectivesRefreshTest02 {

    static Random random = new Random();
    static WhiteBox wb = WhiteBox.getWhiteBox();

    static int callable() {
        int result = 0;
        for (int i = 0; i < 100; i++) {
            result += random.nextInt(100);
        }
        return result;
    }

    static void checkCompilationLevel(Method method, int level) {
        assertEQ(level, wb.getMethodCompilationLevel(method), "Compilation level");
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

    static void clearDirectives() throws Exception {
        new JMXExecutor().execute("Compiler.directives_clear");
    }

    static void deoptimizeMethod(Method method) {
        wb.deoptimizeMethod(method);
    }

    public static class AddRefreshTest01 {
        static Path directivesPath = null;

        static final String DIRECTIVES = """
                    [
                      {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest02::callable",
                        c2: {
                          Exclude: true
                        }
                      }
                    ]
        """;

        public static void run() throws Exception {
            try {
                directivesPath = Files.createTempFile("AddRefreshTest01-", "");
                Files.writeString(directivesPath, DIRECTIVES);

                Method method = DirectivesRefreshTest02.class.getDeclaredMethod("callable");

                clearDirectives();
                deoptimizeMethod(method);
                checkCompilationLevel(method, COMP_LEVEL_NONE);

                compileMethodWithCheck(method, COMP_LEVEL_SIMPLE);
                wb.lockCompilation();
                wb.enqueueMethodForCompilation(method, COMP_LEVEL_FULL_OPTIMIZATION);

                var output = new JMXExecutor().execute("Compiler.directives_add -r " + directivesPath.toString());
                output.stderrShouldBeEmpty().shouldContain("1 compiler directives added");
                wb.unlockCompilation();
                while (wb.isMethodQueuedForCompilation(method)) {
                    Thread.onSpinWait();
                }
                checkCompilationLevel(method, COMP_LEVEL_SIMPLE);

                compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION);
                checkCompilationLevel(method, COMP_LEVEL_SIMPLE);
            } catch (Exception e) {
                try {
                    new JMXExecutor().execute("Compiler.directives_print");
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }

                throw e;
            } finally {
                if (directivesPath != null) {
                    Files.deleteIfExists(directivesPath);
                    directivesPath = null;
                }
            }
        }
    }

    public static class AddRefreshTest02 {
        static Path directivesPath = null;

        static final String DIRECTIVES = """
                    [
                      {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest02::callable",
                        c1: {
                          Exclude: true
                        }
                      }
                    ]
        """;

        public static void run() throws Exception {
            try {
                directivesPath = Files.createTempFile("AddRefreshTest02-", "");
                Files.writeString(directivesPath, DIRECTIVES);

                Method method = DirectivesRefreshTest02.class.getDeclaredMethod("callable");

                clearDirectives();
                deoptimizeMethod(method);
                checkCompilationLevel(method, COMP_LEVEL_NONE);

                compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
                wb.lockCompilation();
                wb.enqueueMethodForCompilation(method, COMP_LEVEL_SIMPLE);

                var output = new JMXExecutor().execute("Compiler.directives_add -r " + directivesPath.toString());
                output.stderrShouldBeEmpty().shouldContain("1 compiler directives added");
                wb.unlockCompilation();
                while (wb.isMethodQueuedForCompilation(method)) {
                    Thread.onSpinWait();
                }
                checkCompilationLevel(method, COMP_LEVEL_FULL_OPTIMIZATION);

                compileMethod(method, COMP_LEVEL_SIMPLE);
                checkCompilationLevel(method, COMP_LEVEL_FULL_OPTIMIZATION);
            } catch (Exception e) {
                try {
                    new JMXExecutor().execute("Compiler.directives_print");
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }

                throw e;
            } finally {
                if (directivesPath != null) {
                    Files.deleteIfExists(directivesPath);
                    directivesPath = null;
                }
            }
        }
    }

    public static class AddRefreshTest03 {
        static Path directivesPath = null;

        static final String DIRECTIVES = """
                    [
                      {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest02::callable",
                        c1: {
                          Exclude: true
                        }
                      }
                    ]
        """;

        public static void run() throws Exception {
            try {
                directivesPath = Files.createTempFile("AddRefreshTest03-", "");
                Files.writeString(directivesPath, DIRECTIVES);

                Method method = DirectivesRefreshTest02.class.getDeclaredMethod("callable");

                clearDirectives();
                deoptimizeMethod(method);
                checkCompilationLevel(method, COMP_LEVEL_NONE);

                compileMethodWithCheck(method, COMP_LEVEL_SIMPLE);
                wb.lockCompilation();
                wb.enqueueMethodForCompilation(method, COMP_LEVEL_FULL_OPTIMIZATION);

                var output = new JMXExecutor().execute("Compiler.directives_add -r " + directivesPath.toString());
                output.stderrShouldBeEmpty().shouldContain("1 compiler directives added");
                wb.unlockCompilation();
                while (wb.isMethodQueuedForCompilation(method)) {
                    Thread.onSpinWait();
                }
                checkCompilationLevel(method, COMP_LEVEL_NONE);

                compileMethod(method, COMP_LEVEL_SIMPLE);
                checkCompilationLevel(method, COMP_LEVEL_NONE);
            } catch (Exception e) {
                try {
                    new JMXExecutor().execute("Compiler.directives_print");
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }

                throw e;
            } finally {
                if (directivesPath != null) {
                    Files.deleteIfExists(directivesPath);
                    directivesPath = null;
                }
            }
        }
    }

    public static class AddRefreshTest04 {
        static Path directivesPath = null;

        static final String DIRECTIVES = """
                    [
                      {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest02::callable",
                        c2: {
                          Exclude: true
                        }
                      }
                    ]
        """;

        public static void run() throws Exception {
            try {
                directivesPath = Files.createTempFile("AddRefreshTest04-", "");
                Files.writeString(directivesPath, DIRECTIVES);

                Method method = DirectivesRefreshTest02.class.getDeclaredMethod("callable");

                clearDirectives();
                deoptimizeMethod(method);
                checkCompilationLevel(method, COMP_LEVEL_NONE);

                compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
                wb.lockCompilation();
                wb.enqueueMethodForCompilation(method, COMP_LEVEL_SIMPLE);

                var output = new JMXExecutor().execute("Compiler.directives_add -r " + directivesPath.toString());
                output.stderrShouldBeEmpty().shouldContain("1 compiler directives added");
                wb.unlockCompilation();
                while (wb.isMethodQueuedForCompilation(method)) {
                    Thread.onSpinWait();
                }
                checkCompilationLevel(method, COMP_LEVEL_NONE);

                compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION);
                checkCompilationLevel(method, COMP_LEVEL_NONE);
            } catch (Exception e) {
                try {
                    new JMXExecutor().execute("Compiler.directives_print");
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }

                throw e;
            } finally {
                if (directivesPath != null) {
                    Files.deleteIfExists(directivesPath);
                    directivesPath = null;
                }
            }
        }
    }

    public static class AddRefreshTest05 {
        static Path directivesPath = null;

        static final String DIRECTIVES = """
                    [
                      {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest02::callable",
                        c2: {
                          Exclude: true
                        }
                      }
                    ]
        """;

        public static void run() throws Exception {
            try {
                directivesPath = Files.createTempFile("AddRefreshTest05-", "");
                Files.writeString(directivesPath, DIRECTIVES);

                Method method = DirectivesRefreshTest02.class.getDeclaredMethod("callable");

                clearDirectives();
                deoptimizeMethod(method);
                checkCompilationLevel(method, COMP_LEVEL_NONE);

                compileMethodWithCheck(method, COMP_LEVEL_SIMPLE);
                wb.deoptimizeMethod(method);
                checkCompilationLevel(method, COMP_LEVEL_NONE);
                wb.lockCompilation();
                wb.enqueueMethodForCompilation(method, COMP_LEVEL_FULL_OPTIMIZATION);

                var output = new JMXExecutor().execute("Compiler.directives_add -r " + directivesPath.toString());
                output.stderrShouldBeEmpty().shouldContain("1 compiler directives added");
                wb.unlockCompilation();
                while (wb.isMethodQueuedForCompilation(method)) {
                    Thread.onSpinWait();
                }
                checkCompilationLevel(method, COMP_LEVEL_NONE);

                compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION);
                checkCompilationLevel(method, COMP_LEVEL_NONE);
            } catch (Exception e) {
                try {
                    new JMXExecutor().execute("Compiler.directives_print");
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }

                throw e;
            } finally {
                if (directivesPath != null) {
                    Files.deleteIfExists(directivesPath);
                    directivesPath = null;
                }
            }
        }
    }

    public static class AddRefreshTest06 {
        static Path directivesPath = null;

        static final String DIRECTIVES = """
                    [
                      {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest02::callable",
                        c1: {
                          Exclude: true
                        }
                      }
                    ]
        """;

        public static void run() throws Exception {
            try {
                directivesPath = Files.createTempFile("AddRefreshTest06-", "");
                Files.writeString(directivesPath, DIRECTIVES);

                Method method = DirectivesRefreshTest02.class.getDeclaredMethod("callable");

                clearDirectives();
                deoptimizeMethod(method);
                checkCompilationLevel(method, COMP_LEVEL_NONE);

                compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
                wb.deoptimizeMethod(method);
                checkCompilationLevel(method, COMP_LEVEL_NONE);
                wb.lockCompilation();
                wb.enqueueMethodForCompilation(method, COMP_LEVEL_SIMPLE);

                var output = new JMXExecutor().execute("Compiler.directives_add -r " + directivesPath.toString());
                output.stderrShouldBeEmpty().shouldContain("1 compiler directives added");
                wb.unlockCompilation();
                while (wb.isMethodQueuedForCompilation(method)) {
                    Thread.onSpinWait();
                }
                checkCompilationLevel(method, COMP_LEVEL_NONE);

                compileMethod(method, COMP_LEVEL_SIMPLE);
                checkCompilationLevel(method, COMP_LEVEL_NONE);
            } catch (Exception e) {
                try {
                    new JMXExecutor().execute("Compiler.directives_print");
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }

                throw e;
            } finally {
                if (directivesPath != null) {
                    Files.deleteIfExists(directivesPath);
                    directivesPath = null;
                }
            }
        }
    }

    public static class AddRefreshTest07 {
        static Path directivesPath = null;

        static final String DIRECTIVES = """
                    [
                      {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest02::callable",
                        c1: {
                          Exclude: true
                        }
                      }
                    ]
        """;

        public static void run() throws Exception {
            try {
                directivesPath = Files.createTempFile("AddRefreshTest07-", "");
                Files.writeString(directivesPath, DIRECTIVES);

                Method method = DirectivesRefreshTest02.class.getDeclaredMethod("callable");

                clearDirectives();
                deoptimizeMethod(method);
                checkCompilationLevel(method, COMP_LEVEL_NONE);

                compileMethodWithCheck(method, COMP_LEVEL_SIMPLE);
                wb.deoptimizeMethod(method);
                checkCompilationLevel(method, COMP_LEVEL_NONE);
                wb.lockCompilation();
                wb.enqueueMethodForCompilation(method, COMP_LEVEL_FULL_OPTIMIZATION);

                var output = new JMXExecutor().execute("Compiler.directives_add -r " + directivesPath.toString());
                output.stderrShouldBeEmpty().shouldContain("1 compiler directives added");
                wb.unlockCompilation();
                while (wb.isMethodQueuedForCompilation(method)) {
                    Thread.onSpinWait();
                }
                checkCompilationLevel(method, COMP_LEVEL_NONE);

                compileMethod(method, COMP_LEVEL_SIMPLE);
                checkCompilationLevel(method, COMP_LEVEL_NONE);
            } catch (Exception e) {
                try {
                    new JMXExecutor().execute("Compiler.directives_print");
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }

                throw e;
            } finally {
                if (directivesPath != null) {
                    Files.deleteIfExists(directivesPath);
                    directivesPath = null;
                }
            }
        }
    }

    public static class AddRefreshTest08 {
        static Path directivesPath = null;

        static final String DIRECTIVES = """
                    [
                      {
                        match: "serviceability.dcmd.compiler.DirectivesRefreshTest02::callable",
                        c2: {
                          Exclude: true
                        }
                      }
                    ]
        """;

        public static void run() throws Exception {
            try {
                directivesPath = Files.createTempFile("AddRefreshTest08-", "");
                Files.writeString(directivesPath, DIRECTIVES);

                Method method = DirectivesRefreshTest02.class.getDeclaredMethod("callable");

                clearDirectives();
                deoptimizeMethod(method);
                checkCompilationLevel(method, COMP_LEVEL_NONE);

                compileMethodWithCheck(method, COMP_LEVEL_FULL_OPTIMIZATION);
                wb.deoptimizeMethod(method);
                checkCompilationLevel(method, COMP_LEVEL_NONE);
                wb.lockCompilation();
                wb.enqueueMethodForCompilation(method, COMP_LEVEL_SIMPLE);

                var output = new JMXExecutor().execute("Compiler.directives_add -r " + directivesPath.toString());
                output.stderrShouldBeEmpty().shouldContain("1 compiler directives added");
                wb.unlockCompilation();
                while (wb.isMethodQueuedForCompilation(method)) {
                    Thread.onSpinWait();
                }
                checkCompilationLevel(method, COMP_LEVEL_NONE);

                compileMethod(method, COMP_LEVEL_FULL_OPTIMIZATION);
                checkCompilationLevel(method, COMP_LEVEL_NONE);
            } catch (Exception e) {
                try {
                    new JMXExecutor().execute("Compiler.directives_print");
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }

                throw e;
            } finally {
                if (directivesPath != null) {
                    Files.deleteIfExists(directivesPath);
                    directivesPath = null;
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        AddRefreshTest01.run();
        AddRefreshTest02.run();
        AddRefreshTest03.run();
        AddRefreshTest04.run();
        AddRefreshTest05.run();
        AddRefreshTest06.run();
        AddRefreshTest07.run();
        AddRefreshTest08.run();
    }
}
