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

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import org.jetbrains.annotations.NotNull;

/**
 * User: ktisha
 *
 * QuickFix to add parameter to unresolved reference
 */
public class UnresolvedReferenceAddParameterQuickFix implements LocalQuickFix {
  private final String myName;
  public UnresolvedReferenceAddParameterQuickFix(String name) {
    myName = name;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.unresolved.reference.add.param.$0", myName);
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.unresolved.reference.add.param");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    final PyNamedParameter parameter = elementGenerator.createParameter(element.getText() + "=None");
    final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (function != null) {
      final PyParameterList parameterList = function.getParameterList();
      parameterList.addParameter(parameter);
      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(parameterList);
      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(parameter);
      builder.replaceRange(TextRange.create(parameter.getTextLength() - 4, parameter.getTextLength()), "None");
      final VirtualFile virtualFile = function.getContainingFile().getVirtualFile();
      if (virtualFile == null) return;
      final Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile), true);
      if (editor == null) return;
      builder.run(editor, false);
    }
  }
}
