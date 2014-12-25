/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.surround.surrounders.statements;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 28, 2009
 * Time: 6:23:51 PM
 */
public class PyWithTryExceptSurrounder extends PyStatementSurrounder {
  @Override
  @Nullable
  protected TextRange surroundStatement(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements)
    throws IncorrectOperationException {
    PyTryExceptStatement tryStatement = PyElementGenerator.getInstance(project).
      createFromText(LanguageLevel.getDefault(), PyTryExceptStatement.class, getTemplate());
    final PsiElement parent = elements[0].getParent();
    final PyStatementList statementList = tryStatement.getTryPart().getStatementList();
    statementList.addRange(elements[0], elements[elements.length - 1]);
    statementList.getFirstChild().delete();
    tryStatement = (PyTryExceptStatement)parent.addBefore(tryStatement, elements[0]);
    parent.deleteChildRange(elements [0], elements[elements.length-1]);

    final PsiFile psiFile = parent.getContainingFile();
    final Document document = psiFile.getViewProvider().getDocument();
    final TextRange range = tryStatement.getTextRange();
    assert document != null;
    final RangeMarker rangeMarker = document.createRangeMarker(range);

    final PsiElement element = psiFile.findElementAt(rangeMarker.getStartOffset());
    tryStatement = PsiTreeUtil.getParentOfType(element, PyTryExceptStatement.class);
    if (tryStatement != null) {
      return getResultRange(tryStatement);
    }
    return null;
  }

  protected String getTemplate() {
    return "try:\n    pass\nexcept:\n    pass";
  }

  protected TextRange getResultRange(PyTryExceptStatement tryStatement) {
    final PyExceptPart part = tryStatement.getExceptParts()[0];
    final PyStatementList list = part.getStatementList();
    return list.getTextRange();
  }

  public String getTemplateDescription() {
    return PyBundle.message("surround.with.try.except.template");
  }
}
