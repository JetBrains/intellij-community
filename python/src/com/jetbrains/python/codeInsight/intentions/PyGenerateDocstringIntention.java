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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocstringGenerator;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

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
    return isAvailableForFunction(project, function);
  }

  private boolean isAvailableForFunction(Project project, PyFunction function) {
    if (function.getDocStringValue() != null) {
      PySignature signature = PySignatureCacheManager.getInstance(project).findSignature(function);

      PyDocstringGenerator docstringGenerator = new PyDocstringGenerator(function);

      docstringGenerator.addFunctionArguments(function, signature);


      if (docstringGenerator.haveParametersToAdd()) {
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

    generateDocstringForFunction(project, editor, function);
  }

  public static void generateDocstringForFunction(Project project, Editor editor, PyFunction function) {
    final Module module = ModuleManager.getInstance(project).getModules()[0];
    final PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(module);
    if (documentationSettings.isPlain(function.getContainingFile())) {
      final String[] values = {DocStringFormat.EPYTEXT, DocStringFormat.REST};
      final int i = Messages.showChooseDialog("Docstring format:", "Select Docstring Type", values, DocStringFormat.EPYTEXT, null);
      if (i < 0) return;
      final String value = values[i];
      documentationSettings.setFormat(value);
    }
    PyDocstringGenerator docstringGenerator = new PyDocstringGenerator(function).withSignatures();

    if (function.getDocStringValue() == null) {
      docstringGenerator.withReturn();
    }

    docstringGenerator.build();
  }
}
