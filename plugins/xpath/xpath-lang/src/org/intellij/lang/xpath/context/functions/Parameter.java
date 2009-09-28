/*
 * Copyright 2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath.context.functions;

import org.intellij.lang.xpath.psi.XPathType;

import org.jetbrains.annotations.NotNull;

public class Parameter {
    public enum Kind {
        REQUIRED, OPTIONAL, VARARG
    }
    public final XPathType type;
    public final Kind kind;

    public Parameter(@NotNull XPathType type, @NotNull Kind kind) {
        this.type = type;
        this.kind = kind;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder(type.getName());
        if (kind == Parameter.Kind.OPTIONAL) sb.append("?");
        if (kind == Parameter.Kind.VARARG) sb.append("*");
        return sb.toString();
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Parameter parameter = (Parameter)o;

        if (kind != parameter.kind) return false;
        return type.equals(parameter.type);
    }

    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + kind.hashCode();
        return result;
    }
}
