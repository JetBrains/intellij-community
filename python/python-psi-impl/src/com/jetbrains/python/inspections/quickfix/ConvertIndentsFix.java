// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.ConvertIndentsUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonCodeStyleService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public class ConvertIndentsFix implements LocalQuickFix {
  private final boolean myToSpaces;

  public ConvertIndentsFix(boolean toSpaces) {
    myToSpaces = toSpaces;
  }

  @Override
  public @NotNull String getName() {
    return myToSpaces ? PyPsiBundle.message("QFIX.convert.indents.to.spaces") : PyPsiBundle.message("QFIX.convert.indents.to.tabs");
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.convert.indents");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiFile file = descriptor.getPsiElement().getContainingFile();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document != null) {
      int tabSize = PythonCodeStyleService.getInstance().getIndentSize(file);
      TextRange allDoc = new TextRange(0, document.getTextLength());
      if (myToSpaces) {
        ConvertIndentsUtil.convertIndentsToSpaces(document, tabSize, allDoc);
      }
      else {
        ConvertIndentsUtil.convertIndentsToTabs(document, tabSize, allDoc);
      }
    }
  }
}
