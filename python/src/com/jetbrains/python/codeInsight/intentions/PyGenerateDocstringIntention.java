// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 * Intention to add documentation string for function
 * (with checked format)
 */
public class PyGenerateDocstringIntention extends PyBaseIntentionAction {
  private String myText;

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.doc.string.stub");
  }

  @NotNull
  @Override
  public String getText() {
    return myText;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile) || file instanceof PyDocstringFile) return false;
    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    if (elementAt == null) {
      return false;
    }
    PyFunction function = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
    final PyStatementList statementList = PsiTreeUtil.getParentOfType(elementAt, PyStatementList.class, false, PyFunction.class);
    if (function == null || statementList != null) {
      return false;
    }
    if (!elementAt.equals(function.getNameNode())) return false;
    return isAvailableForFunction(function);
  }

  private boolean isAvailableForFunction(PyFunction function) {
    if (function.getDocStringValue() != null) {
      if (PyDocstringGenerator.forDocStringOwner(function).withInferredParameters(false).hasParametersToAdd()) {
        myText = PyBundle.message("INTN.add.parameters.to.docstring");
        return true;
      }
      else {
        return false;
      }
    }
    else {
      myText = PyBundle.message("INTN.doc.string.stub");
      return true;
    }
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    PyFunction function = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);

    if (function == null) {
      return;
    }

    generateDocstring(function, editor);
  }

  public static void generateDocstring(@NotNull PyDocStringOwner docStringOwner, @Nullable Editor editor) {
    if (!DocStringUtil.ensureNotPlainDocstringFormat(docStringOwner)) {
      return;
    }
    final PyDocstringGenerator docstringGenerator = PyDocstringGenerator
      .forDocStringOwner(docStringOwner)
      .withInferredParameters(false)
      .addFirstEmptyLine();
    final PyStringLiteralExpression updated = docstringGenerator.buildAndInsert().getDocStringExpression();
    if (updated != null && editor != null) {
      final int offset = updated.getTextOffset();
      editor.getCaretModel().moveToOffset(offset);
      editor.getCaretModel().moveCaretRelatively(0, 1, false, false, false);
    }
  }
}
