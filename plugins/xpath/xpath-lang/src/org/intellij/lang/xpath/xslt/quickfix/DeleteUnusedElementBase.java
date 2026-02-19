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
package org.intellij.lang.xpath.xslt.quickfix;

import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;

public abstract class DeleteUnusedElementBase<T extends XsltVariable> extends LocalQuickFixOnPsiElement {
    private final String myName;

    protected DeleteUnusedElementBase(String name, T element) {
        super(element);
        myName = name;
    }

    @Override
    public @NotNull String getFamilyName() {
        return XPathBundle.message("intention.family.name.delete.unused.element");
    }

    @Override
    public @NotNull String getText() {
        return XPathBundle.message("intention.name.delete.unused", getType(), myName);
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
        try {
            //noinspection unchecked
            deleteElement((T)startElement);
        } catch (IncorrectOperationException e) {
            Logger.getInstance(getClass().getName()).error(e);
        }
    }

    public abstract String getType();

    protected void deleteElement(@NotNull T obj) throws IncorrectOperationException {
        obj.delete();
    }
}