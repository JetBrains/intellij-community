package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.hint.ShowContainerInfoHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;

public class ShowContainerInfoAction extends BaseCodeInsightAction{
  protected CodeInsightActionHandler getHandler() {
    return new ShowContainerInfoHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return file.canContainJavaCode() || file instanceof XmlFile;
  }
}