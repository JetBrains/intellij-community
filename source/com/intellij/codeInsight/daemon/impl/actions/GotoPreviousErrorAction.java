
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.psi.*;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.daemon.impl.GotoPreviousErrorHandler;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;

public class GotoPreviousErrorAction extends BaseCodeInsightAction{
  protected CodeInsightActionHandler getHandler() {
    return new GotoPreviousErrorHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(file);
  }
}