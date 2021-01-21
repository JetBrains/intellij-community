// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class SwitchToHtml5Action implements LocalQuickFix, IntentionAction {
  @NotNull
  @Override
  public String getFamilyName() {
    return XmlAnalysisBundle.message("html.quickfix.switch.to.html5");
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    applyFix(project);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    applyFix(project);
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return null;
  }

  private static void applyFix(Project project) {
    ExternalResourceManagerEx.getInstanceEx().setDefaultHtmlDoctype(Html5SchemaProvider.getHtml5SchemaLocation(), project);
    DaemonCodeAnalyzer.getInstance(project).restart();
  }
}
