package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.completion.CodeCompletionHandler;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileTypes.FileTypeSupportCapabilities;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;


/**
 *
 *
 */
public class CodeCompletionAction extends BaseCodeInsightAction {
  public CodeCompletionAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformedImpl(Project project, Editor editor) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.basic");
    super.actionPerformedImpl(project, editor);
  }

  public CodeInsightActionHandler getHandler() {
    return new CodeCompletionHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    /*
    boolean result = file.canContainJavaCode() || file instanceof XmlFile;
    if (!result) {
      FileTypeSupportCapabilities supportCapabilities = file.getFileType().getSupportCapabilities();

      if (supportCapabilities!=null) result = supportCapabilities.hasCompletion();
    }
    return  result;
    */
    return true;
  }

  protected boolean isValidForLookup() {
    return true;
  }
}
