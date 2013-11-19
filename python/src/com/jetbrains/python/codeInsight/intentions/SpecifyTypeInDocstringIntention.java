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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocstringGenerator;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: ktisha
 * <p/>
 * Helps to specify type
 */
public class SpecifyTypeInDocstringIntention extends TypeIntention {
  private String myText = PyBundle.message("INTN.specify.type");

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.specify.type");
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    PyExpression problemElement = getProblemElement(elementAt);
    PsiReference reference = problemElement == null ? null : problemElement.getReference();

    final PsiElement resolved = reference != null ? reference.resolve() : null;
    PyParameter parameter = getParameter(problemElement, resolved);
    String kind = parameter != null ? "type" : "rtype";

    final Callable callable;
    if (parameter != null) {
      callable = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
    }
    else {
      callable = getCallable(elementAt);
    }
    if (callable instanceof PyFunction) {
      generateDocstring(kind, (PyFunction)callable, problemElement, editor);
    }
  }

  private static void generateDocstring(String kind,
                                        PyFunction pyFunction,
                                        PyExpression problemElement, Editor editor) {
    String name = "rtype".equals(kind) ? "" : StringUtil.notNullize(problemElement.getName());

    final PyDocstringGenerator docstringGenerator = new PyDocstringGenerator(pyFunction);

    PySignature signature = PySignatureCacheManager.getInstance(pyFunction.getProject()).findSignature(pyFunction);
    if (signature != null) {
      docstringGenerator.withParamTypedByQualifiedName(kind, name, signature.getArgTypeQualifiedName(name), pyFunction);
    }
    else {
      docstringGenerator.withParam(kind, name);
    }

    final Module module = ModuleManager.getInstance(pyFunction.getProject()).getModules()[0];
    final PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(module);
    if (documentationSettings.isPlain(pyFunction.getContainingFile())) {
      final String[] values = {DocStringFormat.EPYTEXT, DocStringFormat.REST};
      final int i = Messages.showChooseDialog("Docstring format:", "Select Docstring Type", values, DocStringFormat.EPYTEXT, null);
      if (i < 0) return;
      final String value = values[i];
      documentationSettings.setFormat(value);
    }
    docstringGenerator.build();
    docstringGenerator.startTemplate();
  }

  @Override
  protected void updateText(boolean isReturn) {
    myText = isReturn ? PyBundle.message("INTN.specify.return.type") : PyBundle.message("INTN.specify.type");
  }

  @Override
  protected boolean isParamTypeDefined(@NotNull final PyParameter parameter) {
    PyFunction pyFunction = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
    if (pyFunction != null) {
      final StructuredDocString structuredDocString = pyFunction.getStructuredDocString();
      return structuredDocString != null && structuredDocString.getParamType(StringUtil.notNullize(parameter.getName())) != null;
    }
    return false;
  }

  @Override
  protected boolean isReturnTypeDefined(@NotNull PyFunction function) {
    final StructuredDocString structuredDocString = function.getStructuredDocString();
    return structuredDocString != null && structuredDocString.getReturnType() != null;
  }
}