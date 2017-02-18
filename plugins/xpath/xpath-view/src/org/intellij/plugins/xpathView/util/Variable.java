/*
 * Copyright 2002-2005 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.util;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.Collection;
import java.util.HashSet;

public final class Variable implements Cloneable, Copyable<Variable> {
    private String myName;
    private String myExpression;

    public Variable() {
        this("", "");
    }

    public Variable(String name, String expression) {
        this.myExpression = expression;
        this.myName = name;
    }

    @NotNull
    public String getName() {
        return myName != null ? myName : "";
    }

    public Variable copy() {
        try {
            return (Variable)clone();
        } catch (CloneNotSupportedException e) {
            throw new Error();
        }
    }

    @NotNull
    public String getExpression() {
        return myExpression != null ? myExpression : "";
    }

    public void setName(String s) {
        myName = s;
    }

    public void setExpression(String expression) {
        this.myExpression = expression;
    }

    public String toString() {
        return myName + "<" + myExpression + ">";
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Variable variable = (Variable)o;

        if (myExpression != null ? !myExpression.equals(variable.myExpression) : variable.myExpression != null) return false;
        return !(myName != null ? !myName.equals(variable.myName) : variable.myName != null);
    }

    public int hashCode() {
        int result = (myName != null ? myName.hashCode() : 0);
        result = 29 * result + (myExpression != null ? myExpression.hashCode() : 0);
        return result;
    }

    public static Set<String> asSet(Collection<Variable> second) {
        final HashSet<String> strings = new HashSet<>();
        for (Variable variable : second) {
            strings.add(variable.getName());
        }
        return strings;
    }
}