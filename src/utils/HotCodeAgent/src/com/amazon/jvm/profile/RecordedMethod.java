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

import java.util.Objects;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedFrame;

public final class RecordedMethod {

    private static record Type(long id, String name) {
        public Type {
            Objects.requireNonNull(name);
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof Type that && id == that.id);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(id);
        }

        public static Type from(RecordedClass recordedClass) {
            return new Type(recordedClass.getId(), recordedClass.getName());
        }
    }

    private final Type type;
    private final String name;
    private final String signature;

    private RecordedMethod(Type type, String name, String signature) {
        this.type = Objects.requireNonNull(type);
        this.name = Objects.requireNonNull(name);
        this.signature = Objects.requireNonNull(signature);
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
                (o instanceof RecordedMethod that && type.equals(that.type) && name.equals(that.name)
                        && signature.equals(that.signature));
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, signature);
    }

    public static RecordedMethod from(RecordedFrame frame) {
        var method = frame.getMethod();
        var type = Type.from(method.getType());
        var name = method.getName();
        var signature = method.getDescriptor();
        return new RecordedMethod(type, name, signature);
    }

    @Override
    public String toString() {
        return type.name() + "::" + name + signature;
    }

    public String getName() {
        return name;
    }

    public String getClassName() {
        return type.name();
    }
}
