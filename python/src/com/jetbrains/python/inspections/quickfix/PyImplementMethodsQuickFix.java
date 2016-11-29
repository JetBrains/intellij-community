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

import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.override.PyMethodMember;
import com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Set;

/**
 * User: ktisha
 */
public class PyImplementMethodsQuickFix extends LocalQuickFixOnPsiElement {

  private final Set<PyFunction> myToImplement;

  public PyImplementMethodsQuickFix(PyClass aClass, Set<PyFunction> toImplement) {
   super(aClass);
    myToImplement = toImplement;
  }

  @NotNull
  @Override
  public String getText() {
    return PyBundle.message("QFIX.NAME.implement.methods");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    final Editor editor = PyQuickFixUtil.getEditor(file);

    if (editor != null && startElement instanceof PyClass) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        ArrayList<PyMethodMember> list = new ArrayList<>();
        for (PyFunction function: myToImplement) {
          list.add(new PyMethodMember(function));
        }
        PyOverrideImplementUtil.overrideMethods(editor, (PyClass)startElement, list, true);

      }
      else {
        PyOverrideImplementUtil
          .chooseAndOverrideOrImplementMethods(project, editor,
                                               (PyClass)startElement, myToImplement,
                                               "Select Methods to Implement", true);
      }
    }
  }
}
