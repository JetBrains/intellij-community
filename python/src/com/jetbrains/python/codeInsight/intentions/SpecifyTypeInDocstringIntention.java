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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.documentation.DocStringUtil;
import com.jetbrains.python.documentation.PyDocstringGenerator;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.toolbox.Substring;
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

    final PyCallable callable;
    if (parameter != null) {
      callable = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
    }
    else {
      callable = getCallable(elementAt);
    }
    if (callable instanceof PyFunction) {
      generateDocstring(kind, (PyFunction)callable, problemElement);
    }
  }

  private static void generateDocstring(String kind, PyFunction pyFunction, PyExpression problemElement) {
    if (!DocStringUtil.ensureNotPlainDocstringFormat(pyFunction)) {
      return;
    }

    final boolean isReturn = "rtype".equals(kind);

    final PyDocstringGenerator docstringGenerator = PyDocstringGenerator.forDocStringOwner(pyFunction);
    final PySignature signature = PySignatureCacheManager.getInstance(pyFunction.getProject()).findSignature(pyFunction);
    final String name = isReturn ? "" : StringUtil.notNullize(problemElement.getName());
    final String type;
    if (signature != null) {
      type = StringUtil.notNullize(signature.getArgTypeQualifiedName(name), "object");
    }
    else {
      type = "object";
    }
    if (!isReturn) {
      docstringGenerator.withParamTypedByName(name, type);
    }
    else {
      docstringGenerator.withReturnValue(type);
    }

    docstringGenerator.addFirstEmptyLine().buildAndInsert();
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
      if (structuredDocString == null) {
        return false;
      }
      final Substring typeSub = structuredDocString.getParamTypeSubstring(StringUtil.notNullize(parameter.getName()));
      return typeSub != null && !typeSub.isEmpty();
    }
    return false;
  }

  @Override
  protected boolean isReturnTypeDefined(@NotNull PyFunction function) {
    final StructuredDocString structuredDocString = function.getStructuredDocString();
    return structuredDocString != null && structuredDocString.getReturnType() != null;
  }
}
