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
package org.intellij.lang.xpath.xslt.refactoring;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class XsltRefactoringActionBase implements RefactoringActionHandler {

    public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        final int offset = editor.getCaretModel().getOffset();

        final XmlAttribute context = PsiTreeUtil.getContextOfType(file, XmlAttribute.class, true);
        if (context != null) {
            if (actionPerformedImpl(file, editor, context, offset)) {
                return;
            }
        }

        final String message = getErrorMessage(editor, file, context);
        CommonRefactoringUtil.showErrorHint(editor.getProject(), editor, "Cannot perform refactoring.\n" +
                (message != null ? message : getRefactoringName() + " is not available in the current context."), "XSLT - " + getRefactoringName(), null);
    }

    public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    public String getErrorMessage(Editor editor, PsiFile file, XmlAttribute context) {
        return null;
    }

    public abstract String getRefactoringName();

    protected abstract boolean actionPerformedImpl(PsiFile file, Editor editor, XmlAttribute context, int offset);
}