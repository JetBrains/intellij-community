package com.intellij.spellchecker.actions;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.quickfixes.SpellCheckerQuickFix;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spelling action group.
 */
public final class SpellingPopupActionGroup extends ActionGroup {
  public SpellingPopupActionGroup() {
  }

  public SpellingPopupActionGroup(String shortName, boolean popup) {
    super(shortName, popup);
  }

  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e != null) {
      AnAction[] children = findActions(e);
      // No actions
      if (children.length == 0) {
        e.getPresentation().setEnabled(false);
      }
      return children;
    }
    return AnAction.EMPTY_ARRAY;
  }

  @NotNull
  private static AnAction[] findActions(@NotNull AnActionEvent e) {
    PsiFile psiFile = e.getData(DataKeys.PSI_FILE);
    Project project = e.getData(DataKeys.PROJECT);
    Editor editor = e.getData(DataKeys.EDITOR);
    if (psiFile != null && project != null && editor != null) {
      List<HighlightInfo.IntentionActionDescriptor> quickFixes = QuickFixAction.getAvailableActions(editor, psiFile, -1);
      Map<Anchor, List<AnAction>> children = new HashMap<Anchor, List<AnAction>>();
      ArrayList<AnAction> first = new ArrayList<AnAction>();
      children.put(Anchor.FIRST, first);
      ArrayList<AnAction> last = new ArrayList<AnAction>();
      children.put(Anchor.LAST, last);
      extractActions(quickFixes, children);
      if (first.size() > 0 && last.size() > 0) {
        first.add(new Separator());
      }
      first.addAll(last);
      if (first.size() > 0) {
        return first.toArray(new AnAction[first.size()]);
      }
    }

    return AnAction.EMPTY_ARRAY;
  }

  private static void extractActions(List<HighlightInfo.IntentionActionDescriptor> descriptors, Map<Anchor, List<AnAction>> actions) {
    for (HighlightInfo.IntentionActionDescriptor actionDescriptor : descriptors) {
      IntentionAction action = actionDescriptor.getAction();
      if (action instanceof QuickFixWrapper) {
        QuickFixWrapper wrapper = (QuickFixWrapper)action;
        LocalQuickFix localQuickFix = wrapper.getFix();
        if (localQuickFix instanceof SpellCheckerQuickFix) {
          SpellCheckerQuickFix spellCheckerQuickFix = (SpellCheckerQuickFix)localQuickFix;
          Anchor anchor = spellCheckerQuickFix.getPopupActionAnchor();
          SpellCheckerIntentionAction popupAction = new SpellCheckerIntentionAction(action);
          List<AnAction> list = actions.get(anchor);
          if (list != null) {
            list.add(popupAction);
          }
        }
      }
    }
  }

  public void update(AnActionEvent e) {
    super.update(e);
    if (e != null) {
      if (e.getPresentation().isVisible() && findActions(e).length == 0) {
        e.getPresentation().setVisible(false);
      }
    }
  }

  private static class SpellCheckerIntentionAction extends AnAction {
    private static final Logger LOGGER = Logger.getInstance("#SpellCheckerAction");
    private final IntentionAction intention;

    public SpellCheckerIntentionAction(IntentionAction intention) {
      super(intention.getText());
      this.intention = intention;
    }

    public void actionPerformed(final AnActionEvent e) {
      final PsiFile psiFile = e.getData(DataKeys.PSI_FILE);
      final Project project = e.getData(DataKeys.PROJECT);
      final Editor editor = e.getData(DataKeys.EDITOR);
      if (psiFile != null && project != null && editor != null) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            CommandProcessor.getInstance().executeCommand(project, new Runnable() {
              public void run() {
                try {
                  intention.invoke(project, editor, psiFile);
                }
                catch (IncorrectOperationException ex) {
                  LOGGER.error(ex);
                }
              }
            }, e.getPresentation().getText(), e.getActionManager().getId(SpellCheckerIntentionAction.this));
          }
        });
      }
    }
  }
}
