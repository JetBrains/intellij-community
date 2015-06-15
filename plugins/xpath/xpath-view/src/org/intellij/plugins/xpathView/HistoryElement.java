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
package org.intellij.plugins.xpathView;

import org.intellij.plugins.xpathView.util.Copyable;
import org.intellij.plugins.xpathView.util.Namespace;
import org.intellij.plugins.xpathView.util.Variable;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public final class HistoryElement implements Copyable<HistoryElement> {
    public static final HistoryElement EMPTY = new HistoryElement();

    public final String expression;
    public final Collection<Variable> variables;
    public final Collection<Namespace> namespaces;

    public HistoryElement(String expression, @NotNull Collection<Variable> variables, @NotNull Collection<Namespace> namespaces) {
        this.expression = expression;
        this.variables = Collections.unmodifiableCollection(variables);
        this.namespaces = Collections.unmodifiableCollection(namespaces);
    }

    @SuppressWarnings({"unchecked"})
    private HistoryElement() {
        expression = null;
        variables = Collections.emptySet();
        namespaces = Collections.emptySet();
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final HistoryElement that = (HistoryElement)o;

        return !(expression != null ? !expression.equals(that.expression) : that.expression != null); 
    }

    public int hashCode() {
        return (expression != null ? expression.hashCode() : 0);
    }

    public HistoryElement copy() {
        return new HistoryElement(expression, Copyable.Util.copy(this.variables), Copyable.Util.copy(namespaces));
    }

    public HistoryElement changeContext(Collection<Namespace> namespaces, Collection<Variable> variables) {
        return new HistoryElement(expression, variables, namespaces);
    }

    public HistoryElement changeExpression(String expression) {
        return new HistoryElement(expression, variables, namespaces);
    }

    @Override
    public String toString() {
        return expression;
    }
}
