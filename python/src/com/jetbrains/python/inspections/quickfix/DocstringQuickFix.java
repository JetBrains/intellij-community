/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.intentions.PyGenerateDocstringIntention;
import com.jetbrains.python.documentation.PyDocstringGenerator;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.editor.PythonDocCommentUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public class DocstringQuickFix implements LocalQuickFix {
  String myMissingText;
  String myUnexpected;
  String myPrefix;

  public DocstringQuickFix(String missing, String unexpected) {
    myMissingText = missing;
    myUnexpected = unexpected;
  }

  @NotNull
  public String getName() {
    if (myMissingText != null) {
      return PyBundle.message("QFIX.docstring.add.$0", myMissingText);
    }
    else if (myUnexpected != null) {
      return PyBundle.message("QFIX.docstring.remove.$0", myUnexpected);
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
  private static Editor getEditor(Project project, PsiFile file) {
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
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
    if (docStringExpression == null && myMissingText == null && myUnexpected == null) {
      addEmptyDocstring(project, docStringOwner);
      return;
    }
    if (docStringExpression != null) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      final Module module = ModuleUtilCore.findModuleForPsiElement(docStringExpression);
      if (module == null) return;
      PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(module);
      if (documentationSettings.isEpydocFormat(docStringExpression.getContainingFile())) {
        myPrefix = "@";
      }
      else {
        myPrefix = ":";
      }

      String replacement = docStringExpression.getText();
      if (myMissingText != null) {
        replacement = createMissingReplacement(docStringOwner);
      }
      if (myUnexpected != null) {
        replacement = PythonDocCommentUtil.removeParamFromDocstring(replacement, myPrefix, myUnexpected);
      }
      if (!replacement.equals(docStringExpression.getText()) && !StringUtil.isEmptyOrSpaces(replacement)) {
        PyExpression str = elementGenerator.createDocstring(replacement).getExpression();
        docStringExpression.replace(str);
      }
    }
  }

  private static void addEmptyDocstring(Project project, PyDocStringOwner docStringOwner) {
    if (docStringOwner instanceof PyFunction) {
      PyGenerateDocstringIntention
        .generateDocstringForFunction(project, getEditor(project, docStringOwner.getContainingFile()), (PyFunction)docStringOwner);
    }
    if (docStringOwner instanceof PyClass) {
      PyFunction init = ((PyClass)docStringOwner).findInitOrNew(false);
      if (init == null) return;
      PythonDocumentationProvider.insertDocStub(init, ((PyClass)docStringOwner).getStatementList(),
                                                project, getEditor(project, docStringOwner.getContainingFile()));
    }
  }

  private String createMissingReplacement(PyDocStringOwner docStringOwner) {
    return new PyDocstringGenerator(docStringOwner).withParam("param", myMissingText).docStringAsText();
  }
}
