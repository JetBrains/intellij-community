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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * User: ktisha
 */
public class PyImplementMethodsQuickFix implements LocalQuickFix {

  private final PyClass myClass;
  private final Set<PyFunction> myToImplement;

  public PyImplementMethodsQuickFix(PyClass aClass, Set<PyFunction> toBeImplemented) {
    myClass = aClass;
    myToImplement = toBeImplemented;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.implement.methods");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final Editor editor = getEditor(project, descriptor.getPsiElement().getContainingFile());
    if (editor != null)
      PyOverrideImplementUtil.chooseAndOverrideOrImplementMethods(project, editor, myClass, myToImplement, "Select Methods to Implement", true);
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

}
