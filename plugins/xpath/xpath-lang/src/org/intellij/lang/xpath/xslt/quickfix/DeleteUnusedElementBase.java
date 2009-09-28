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

import org.intellij.lang.xpath.xslt.psi.XsltVariable;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class DeleteUnusedElementBase<T extends XsltVariable> implements LocalQuickFix {
    private final String myName;
    private final T myElement;

    protected DeleteUnusedElementBase(String name, T element) {
        myName = name;
        myElement = element;
    }

    @NotNull
    public String getFamilyName() {
        return "Delete Unused Element";
    }

    @NotNull
    public String getName() {
        return "Delete unused " + getType() + " '" + myName + "'";
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        try {
            deleteElement(myElement);
        } catch (IncorrectOperationException e) {
            Logger.getInstance(getClass().getName()).error(e);
        }
    }

    public abstract String getType();

    protected void deleteElement(@NotNull T obj) throws IncorrectOperationException {
        obj.delete();
    }
}