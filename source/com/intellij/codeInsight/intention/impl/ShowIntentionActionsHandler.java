package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;

/**
 * @author mike
 */
public class ShowIntentionActionsHandler implements CodeInsightActionHandler {
  public void invoke(Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (HintManager.getInstance().performCurrentQuestionAction()) return;

    if (!file.isWritable()) return;
    if (file instanceof PsiCodeFragment) return;

    TemplateState state = TemplateManagerImpl.getTemplateState(editor);
    if (state != null && !state.isFinished()) return;

    final IntentionAction[] intentionActions = IntentionManager.getInstance(project).getIntentionActions();

    ArrayList intentionsToShow = new ArrayList();
    ArrayList fixesToShow = new ArrayList();
    for(int i = 0; i < intentionActions.length; i++){
      IntentionAction action = intentionActions[i];
      if (action instanceof IntentionActionComposite) {

        if (action instanceof QuickFixAction) {
          fixesToShow.addAll(((IntentionActionComposite)action).getAvailableActions(editor, file));
        } else {
          intentionsToShow.addAll(((IntentionActionComposite)action).getAvailableActions(editor, file));
        }
      } else if (action.isAvailable(project, editor, file)){
        intentionsToShow.add(action);
      }
    }

    if (!intentionsToShow.isEmpty() || !fixesToShow.isEmpty()) {
      IntentionHintComponent.showIntentionHint(project, editor, intentionsToShow, fixesToShow, true);
    }
    else {
//      Toolkit.getDefaultToolkit().beep();
    }
  }

  public boolean startInWriteAction() {
    return false;
  }
}
