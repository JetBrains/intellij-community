// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class PyMakePublicQuickFix implements LocalQuickFix {

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.make.public");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = PyQuickFixUtil.dereference(descriptor.getPsiElement());
    if (element == null) {
      return;
    }
    if (element instanceof PyTargetExpression) {
      final String name = ((PyTargetExpression)element).getName();
      if (name == null) return;
      final VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
      if (virtualFile != null) {
        final String publicName = StringUtil.trimLeading(name, '_');
        PythonUiService.getInstance().runRenameProcessor(project, element, publicName, false, false);
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
