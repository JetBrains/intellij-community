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

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class AbstractLookup extends LookupElement {
    protected final String myName;
    protected final String myPresentation;

    AbstractLookup(String name, String presentation) {
        this.myName = name;
        this.myPresentation = presentation;
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
        context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), myName);
        context.getEditor().getCaretModel().moveToOffset(context.getTailOffset());
        XPathInsertHandler.handleInsert(context, this);
    }

    public String getName() {
        return myName;
    }

    @Override
    public @NotNull String getLookupString() {
        return myPresentation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final AbstractLookup that = (AbstractLookup)o;

        return myName.equals(that.myName);
    }

    @Override
    public int hashCode() {
        return myName.hashCode();
    }
}
