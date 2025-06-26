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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Properties;

public class HotCodeAgentConfigurationTest {

    @Test
    public void testFromProperties() throws IOException {
        Properties props = new Properties();
        props.setProperty("profiling.delay", "10m");

        HotCodeAgentConfiguration config = HotCodeAgentConfiguration.from(props);

        assertEquals(Duration.ofMinutes(10), config.profiling.delay());
    }

    @Test
    public void testDefaultExcludeList() throws IOException {
        HotCodeAgentConfiguration.MethodExcludeList excludeList = new HotCodeAgentConfiguration.MethodExcludeList();
        assertFalse(excludeList.contains("A", "a"));
        String[] compileCommandNames = new String[] {
            "help",
            "quiet",
            "log",
            "print",
            "inline",
            "dontinline",
            "blackhole",
            "compileonly",
            "exclude",
            "break",
            "option",
            "unknown"
        };
        for (String name : compileCommandNames) {
            assertTrue(excludeList.contains("A", name));
        }
    }

    @Test
    public void testExcludeList01() throws IOException {
        String excludedMethods = "java.lang.Integer.toString";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertTrue(excludeList.contains("java.lang.Integer", "toString"));
            assertFalse(excludeList.contains("java.lang.Integer", "toInt"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList02() throws IOException {
        String excludedMethods = "java.lang.*.*";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertTrue(excludeList.contains("java.lang.Integer", "toString"));
            assertTrue(excludeList.contains("java.lang.C", "abc"));
            assertFalse(excludeList.contains("B", "toString"));
            assertFalse(excludeList.contains("java.lang.a.B", "toString"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList03() throws IOException {
        String excludedMethods = "*.toString";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertTrue(excludeList.contains("java.lang.Integer", "toString"));
            assertFalse(excludeList.contains("java.lang.C", "abc"));
            assertTrue(excludeList.contains("B", "toString"));
            assertTrue(excludeList.contains("java.lang.a.B", "toString"));
            assertTrue(excludeList.contains("a.B.C", "toString"));
            assertTrue(excludeList.contains("a.B", "toString"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList04() throws IOException {
        String excludedMethods = "java.lang.*.toString";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertTrue(excludeList.contains("java.lang.Integer", "toString"));
            assertTrue(excludeList.contains("java.lang.C", "toString"));
            assertFalse(excludeList.contains("java_lang_Integer", "toString"));
            assertFalse(excludeList.contains("java.lang.C", "abc"));
            assertFalse(excludeList.contains("B", "toString"));
            assertFalse(excludeList.contains("java.lang.a.B", "toString"));
            assertFalse(excludeList.contains("a.B.C", "toString"));
            assertFalse(excludeList.contains("a.B", "toString"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList05() throws IOException {
        String excludedMethods = "*";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertTrue(excludeList.contains("java.lang.Integer", "toString"));
            assertTrue(excludeList.contains("java.lang.C", "toString"));
            assertTrue(excludeList.contains("java_lang_Integer", "toString"));
            assertTrue(excludeList.contains("java.lang.C", "abc"));
            assertTrue(excludeList.contains("B", "toString"));
            assertTrue(excludeList.contains("java.lang.a.B", "toString"));
            assertTrue(excludeList.contains("a.B.C", "toString"));
            assertTrue(excludeList.contains("a.B", "toString"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList06() throws IOException {
        String excludedMethods = "*.get*";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertTrue(excludeList.contains("java.lang.Integer", "getValue"));
            assertFalse(excludeList.contains("Integer", "GetValue"));
            assertTrue(excludeList.contains("a.B.C", "getString"));
            assertTrue(excludeList.contains("B", "get"));
            assertFalse(excludeList.contains("a.B.C", "toString"));
            assertFalse(excludeList.contains("B", "toString"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList07() throws IOException {
        String excludedMethods = "java.lang.Integer.*Value";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertTrue(excludeList.contains("java.lang.Integer", "intValue"));
            assertTrue(excludeList.contains("java.lang.Integer", "longValue"));
            assertTrue(excludeList.contains("java.lang.Integer", "Value"));
            assertFalse(excludeList.contains("java.lang.Integer", "value"));
            assertFalse(excludeList.contains("Integer", "GetValue"));
            assertFalse(excludeList.contains("java.lang.Integer.Value", "intValue"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList08() throws IOException {
        String excludedMethods = "A.toString";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertTrue(excludeList.contains("A", "toString"));
            assertFalse(excludeList.contains("A$B", "toString"));
            assertFalse(excludeList.contains("java.lang.Integer", "value"));
            assertFalse(excludeList.contains("java.lang.A", "toString"));
            assertFalse(excludeList.contains("B", "toString"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList09() throws IOException {
        String excludedMethods = "A$B.toString";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertFalse(excludeList.contains("A", "toString"));
            assertTrue(excludeList.contains("A$B", "toString"));
            assertFalse(excludeList.contains("java.lang.Integer", "value"));
            assertFalse(excludeList.contains("A$B$C", "toString"));
            assertFalse(excludeList.contains("java.lang.A", "toString"));
            assertFalse(excludeList.contains("B", "toString"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList10() throws IOException {
        String excludedMethods = "A$B.*";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertFalse(excludeList.contains("A", "toString"));
            assertTrue(excludeList.contains("A$B", "toString"));
            assertTrue(excludeList.contains("A$B", "get"));
            assertFalse(excludeList.contains("java.lang.Integer", "value"));
            assertFalse(excludeList.contains("A$B$C", "toString"));
            assertFalse(excludeList.contains("java.lang.A", "toString"));
            assertFalse(excludeList.contains("B", "toString"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList11() throws IOException {
        String excludedMethods = "A.*";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertTrue(excludeList.contains("A", "toString"));
            assertTrue(excludeList.contains("A", "a"));
            assertFalse(excludeList.contains("A$B", "toString"));
            assertFalse(excludeList.contains("A$B", "get"));
            assertFalse(excludeList.contains("java.lang.Integer", "value"));
            assertFalse(excludeList.contains("A$B$C", "toString"));
            assertFalse(excludeList.contains("java.lang.A", "toString"));
            assertFalse(excludeList.contains("B", "toString"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList12() throws IOException {
        String excludedMethods = "a.b.c.D$E.toString";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertTrue(excludeList.contains("a.b.c.D$E", "toString"));
            assertFalse(excludeList.contains("D", "toString"));
            assertFalse(excludeList.contains("java.lang.Integer", "value"));
            assertFalse(excludeList.contains("A$B$C", "toString"));
            assertFalse(excludeList.contains("java.lang.A", "toString"));
            assertFalse(excludeList.contains("B", "toString"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList13() throws IOException {
        String excludedMethods = "*.toString\n";
        excludedMethods += "java.lang.*.value\n";
        excludedMethods += "*.lang.*.value\n";
        excludedMethods += "A.a";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertTrue(excludeList.contains("a.b.c.D$E", "toString"));
            assertTrue(excludeList.contains("A", "a"));
            assertTrue(excludeList.contains("java.lang.Integer", "value"));
            assertTrue(excludeList.contains("D", "toString"));
            assertTrue(excludeList.contains("A$B$C", "toString"));
            assertTrue(excludeList.contains("java.lang.A", "value"));
            assertTrue(excludeList.contains("javax.lang.A", "value"));
            assertFalse(excludeList.contains("javax.lang.a.A", "value"));
            assertFalse(excludeList.contains("java.B", "value"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList14() throws IOException {
        String excludedMethods = "*.*.*.*.toString\n";
        excludedMethods += "*.*.*.*.*.value";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertTrue(excludeList.contains("a.b.C.D", "toString"));
            assertTrue(excludeList.contains("a.b.c.D$E", "toString"));
            assertTrue(excludeList.contains("a.b.C.D.E", "value"));
            assertTrue(excludeList.contains("javax.lang.a.b.C", "value"));
            assertFalse(excludeList.contains("A", "value"));
            assertFalse(excludeList.contains("a.B", "toString"));
            assertFalse(excludeList.contains("a.b.C", "toString"));
            assertFalse(excludeList.contains("a.b.C.D.E", "toString"));
            assertFalse(excludeList.contains("a.b.C.D.E.F", "toString"));
            assertFalse(excludeList.contains("java.lang.Integer", "toString"));
            assertFalse(excludeList.contains("D", "toString"));
            assertFalse(excludeList.contains("A$B$C", "toString"));
            assertFalse(excludeList.contains("java.lang.A", "value"));
            assertFalse(excludeList.contains("javax.lang.A", "value"));
            assertFalse(excludeList.contains("java.B", "value"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList15() throws IOException {
        String excludedMethods = "*.*";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertTrue(excludeList.contains("java.lang.Integer", "toString"));
            assertTrue(excludeList.contains("java.lang.C", "toString"));
            assertTrue(excludeList.contains("java_lang_Integer", "toString"));
            assertTrue(excludeList.contains("java.lang.C", "abc"));
            assertTrue(excludeList.contains("B", "toString"));
            assertTrue(excludeList.contains("java.lang.a.B", "toString"));
            assertTrue(excludeList.contains("a.B.C", "toString"));
            assertTrue(excludeList.contains("a.B", "toString"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList16() throws IOException {
        String excludedMethods = ".*.a";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            assertThrows(IllegalArgumentException.class, () -> {
                var excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            });
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList17() throws IOException {
        String excludedMethods = "A.a*.";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            assertThrows(IllegalArgumentException.class, () -> {
                var excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            });
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList18() throws IOException {
        String excludedMethods = "A*a";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            assertThrows(IllegalArgumentException.class, () -> {
                var excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            });
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList19() throws IOException {
        String excludedMethods = "A..*a";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            assertThrows(IllegalArgumentException.class, () -> {
                var excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            });
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }

    @Test
    public void testExcludeList20() throws IOException {
        String excludedMethods = "com.oracle.truffle.runtime.OptimizedCallTarget.*";
        var excludeListFile = Files.createTempFile("exclude-list", "");
        try {
            Files.writeString(excludeListFile, excludedMethods);
            HotCodeAgentConfiguration.MethodExcludeList excludeList = HotCodeAgentConfiguration.MethodExcludeList.fromFile(excludeListFile.toString());
            assertTrue(excludeList.contains("com.oracle.truffle.runtime.OptimizedCallTarget", "call"));
            assertTrue(excludeList.contains("com.oracle.truffle.runtime.OptimizedCallTarget", "profiledPERoot"));
            assertTrue(excludeList.contains("com.oracle.truffle.runtime.OptimizedCallTarget", "callBoundary"));
            assertFalse(excludeList.contains("java.lang.Integer", "value"));
            assertFalse(excludeList.contains("Integer", "GetValue"));
            assertFalse(excludeList.contains("java.lang.Integer.Value", "intValue"));
        } finally {
            Files.deleteIfExists(excludeListFile);
        }
    }
}
