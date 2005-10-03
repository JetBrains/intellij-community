package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.GenerateDelegateHandler;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * @author mike
 */
public class GenerateDelegateAction extends BaseCodeInsightAction {
  private GenerateDelegateHandler myHandler = new GenerateDelegateHandler();

  protected CodeInsightActionHandler getHandler() {
    return myHandler;
  }

  protected boolean isValidForFile(Project project, Editor editor, PsiFile file) {
    if (!file.canContainJavaCode()) return false;
    return OverrideImplementUtil.getContextClass(project, editor, file, false) != null &&
           myHandler.isApplicable(file, editor);
  }
}
