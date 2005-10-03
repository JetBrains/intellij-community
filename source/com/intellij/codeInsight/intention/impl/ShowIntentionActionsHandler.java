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
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.List;

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

    ArrayList<Pair<IntentionAction,List<IntentionAction>>> intentionsToShow = new ArrayList<Pair<IntentionAction,List<IntentionAction>>>();
    ArrayList<Pair<IntentionAction,List<IntentionAction>>> fixesToShow = new ArrayList<Pair<IntentionAction,List<IntentionAction>>>();
    for (IntentionAction action : intentionActions) {
      if (action instanceof IntentionActionComposite) {

        if (action instanceof QuickFixAction) {
          fixesToShow.addAll(((IntentionActionComposite)action).getAvailableActions(editor, file));
        }
        else {
          intentionsToShow.addAll(((IntentionActionComposite)action).getAvailableActions(editor, file));
        }
      }
      else if (action.isAvailable(project, editor, file)) {
        List<IntentionAction> enableDisableIntentionAction = new ArrayList<IntentionAction>();
        enableDisableIntentionAction.add(new IntentionHintComponent.EnableDisableIntentionAction(action));
        intentionsToShow.add(new Pair<IntentionAction, List<IntentionAction>>(action, enableDisableIntentionAction));
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
