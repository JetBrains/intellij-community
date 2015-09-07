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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
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
public class PyGenerateDocstringIntention extends BaseIntentionAction {
  private String myText;

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.doc.string.stub");
  }

  @NotNull
  @Override
  public String getText() {
    return myText;
  }

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

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(file)) {
      return;
    }

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
