
package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.navigation.MethodDownHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.xml.XmlFile;

/**
 *
 */
public class MethodDownAction extends BaseCodeInsightAction {
  protected CodeInsightActionHandler getHandler() {
    return new MethodDownHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return file instanceof PsiJavaFile || file instanceof XmlFile;
  }
}