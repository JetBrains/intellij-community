// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.unresolved.reference.add.param.$0", myName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.unresolved.reference.add.param");
  }

  @Override
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
