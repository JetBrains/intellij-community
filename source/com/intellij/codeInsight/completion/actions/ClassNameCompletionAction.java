package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.completion.ClassNameCompletionHandler;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;

/**
 *
 */
public class ClassNameCompletionAction extends BaseCodeInsightAction {
  public ClassNameCompletionAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformedImpl(Project project, Editor editor) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.classname");
    super.actionPerformedImpl(project, editor);
  }

  protected CodeInsightActionHandler getHandler() {
    return new ClassNameCompletionHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return file.canContainJavaCode() || file instanceof XmlFile;
  }

  protected boolean isValidForLookup() {
    return true;
  }
}
