// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public String getTemplateDescription() {
    return PyBundle.message("surround.with.try.except.template");
  }
}
