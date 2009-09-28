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
package org.intellij.lang.xpath.completion;

import com.intellij.codeInsight.lookup.DeferredUserLookupValue;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupValueWithPriority;
import com.intellij.codeInsight.lookup.LookupValueWithUIHint;
import com.intellij.openapi.project.Project;

import java.awt.*;

abstract class AbstractLookup implements DeferredUserLookupValue, LookupValueWithPriority, LookupValueWithUIHint, Lookup {
    protected final String myName;
    protected final String myPresentation;

    public AbstractLookup(String name, String presentation) {
        this.myName = name;
        this.myPresentation = presentation;
    }

    public int getPriority() {
        return HIGHER; // stay above all word-completion stuff in XSLT
    }

    public boolean handleUserSelection(LookupItem lookupItem, Project project) {
        lookupItem.setLookupString(myName);
        return true;
    }

    public String getName() {
        return myName;
    }

    public String getPresentation() {
        return myPresentation;
    }

    @SuppressWarnings({"ConstantConditions"})
    public Color getColorHint() {
        return null;
    }

    public String getTypeHint() {
        return "";
    }

    public boolean isFunction() {
        return false;
    }

    public boolean hasParameters() {
        return false;
    }

    public boolean isKeyword() {
        return false;
    }

    public boolean isBold() {
        return isKeyword();
    }

    public String toString() {
        return myPresentation;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final AbstractLookup that = (AbstractLookup)o;

        return myName.equals(that.myName);
    }

    public int hashCode() {
        return myName.hashCode();
    }
}
