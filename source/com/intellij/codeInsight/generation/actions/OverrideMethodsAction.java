package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.OverrideMethodsHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 *
 */
public class OverrideMethodsAction extends BaseCodeInsightAction {

  protected CodeInsightActionHandler getHandler() {
    return new OverrideMethodsHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return file.canContainJavaCode() && OverrideImplementUtil.getContextClass(project, editor, file) != null;
  }
}