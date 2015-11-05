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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.intentions.PyGenerateDocstringIntention;
import com.jetbrains.python.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public class DocstringQuickFix implements LocalQuickFix {
  private final SmartPsiElementPointer<PyNamedParameter> myMissingParam;
  private final String myUnexpectedParamName;

  public DocstringQuickFix(@Nullable PyNamedParameter missing, @Nullable  String unexpectedParamName) {
    if (missing != null) {
      myMissingParam = SmartPointerManager.getInstance(missing.getProject()).createSmartPsiElementPointer(missing);
    }
    else {
      myMissingParam = null;
    }
    myUnexpectedParamName = unexpectedParamName;
  }

  @NotNull
  public String getName() {
    if (myMissingParam != null) {
      final PyNamedParameter param = myMissingParam.getElement();
      if (param == null) {
        throw new IncorrectOperationException("Parameter was invalidates before quickfix is called");
      }
      return PyBundle.message("QFIX.docstring.add.$0", param.getName());
    }
    else if (myUnexpectedParamName != null) {
      return PyBundle.message("QFIX.docstring.remove.$0", myUnexpectedParamName);
    }
    else {
      return PyBundle.message("QFIX.docstring.insert.stub");
    }
  }

  @NotNull
  public String getFamilyName() {
    return "Fix docstring";
  }

  @Nullable
  private static Editor getEditor(@NotNull PsiElement element) {
    Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());
    if (document != null) {
      final EditorFactory instance = EditorFactory.getInstance();
      if (instance == null) return null;
      Editor[] editors = instance.getEditors(document);
      if (editors.length > 0) {
        return editors[0];
      }
    }
    return null;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PyDocStringOwner.class);
    if (docStringOwner == null) return;
    PyStringLiteralExpression docStringExpression = docStringOwner.getDocStringExpression();
    if (docStringExpression == null && myMissingParam == null && myUnexpectedParamName == null) {
      addEmptyDocstring(docStringOwner);
      return;
    }
    if (docStringExpression != null) {
      final PyDocstringGenerator generator = PyDocstringGenerator.forDocStringOwner(docStringOwner);
      if (myMissingParam != null) {
        final PyNamedParameter param = myMissingParam.getElement();
        if (param != null) {
          generator.withParam(param);
        }
      }
      else if (myUnexpectedParamName != null) {
        generator.withoutParam(myUnexpectedParamName.trim());
      }
      generator.buildAndInsert();
    }
  }

  private static void addEmptyDocstring(@NotNull PyDocStringOwner docStringOwner) {
    if (docStringOwner instanceof PyFunction ||
        docStringOwner instanceof PyClass && ((PyClass)docStringOwner).findInitOrNew(false, null) != null) {
      PyGenerateDocstringIntention.generateDocstring(docStringOwner, getEditor(docStringOwner));
    }
  }
}
