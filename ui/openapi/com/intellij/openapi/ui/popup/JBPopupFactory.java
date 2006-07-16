package com.intellij.openapi.ui.popup;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.*;

/**
 * @author mike
 */
public abstract class JBPopupFactory {
  public static JBPopupFactory getInstance() {
    return ApplicationManager.getApplication().getComponent(JBPopupFactory.class);
  }

  public PopupChooserBuilder createListPopupBuilder(JList list) {
    return new PopupChooserBuilder(list);
  }

  public abstract ListPopup createConfirmation(String title, Runnable onYes, int defaultOptionIndex);
  public abstract ListPopup createConfirmation(String title, String yesText, String noText, Runnable onYes, int defaultOptionIndex);
  public abstract ListPopup createConfirmation(String title, String yesText, String noText, Runnable onYes, Runnable onNo, int defaultOptionIndex);

  public abstract ListPopupStep createActionsStep(ActionGroup actionGroup,
                                                  DataContext dataContext,
                                                  boolean showNumbers,
                                                  boolean showDisabledActions,
                                                  String title,
                                                  Component component,
                                                  boolean honorActionMnemonics);

  public abstract ListPopupStep createActionsStep(ActionGroup actionGroup,
                                                  DataContext dataContext,
                                                  boolean showNumbers,
                                                  boolean showDisabledActions,
                                                  String title,
                                                  Component component,
                                                  boolean honorActionMnemonics,
                                                  int defaultOptionIndex);

  public enum ActionSelectionAid {
    NUMBERING,
    SPEEDSEARCH,
    MNEMONICS
  }

  public abstract ListPopup createActionGroupPopup(String title,
                                                   ActionGroup actionGroup,
                                                   DataContext dataContext,
                                                   ActionSelectionAid selectionAidMethod,
                                                   boolean showDisabledActions);

  public abstract ListPopup createActionGroupPopup(String title,
                                                   ActionGroup actionGroup,
                                                   DataContext dataContext,
                                                   ActionSelectionAid selectionAidMethod,
                                                   boolean showDisabledActions,
                                                   Runnable disposeCallback);


  public abstract ListPopup createWizardStep(PopupStep step);

  public abstract TreePopup createTree(JBPopup parent, TreePopupStep step, Object parentValue);
  public abstract TreePopup createTree(TreePopupStep step);

  public abstract ComponentPopupBuilder createComponentPopupBuilder(JComponent content, JComponent prefferableFocusComponent);

  /**
   * @return location as close as possible to the action origin. Method has special handling of
   *         the following components:<br>
   *         - caret offset for editor<br>
   *         - current selected node for tree<br>
   *         - current selected row for list<br>
   */
  public abstract RelativePoint guessBestPopupLocation(DataContext dataContext);

  public abstract RelativePoint guessBestPopupLocation(Editor editor);

  public abstract Point getCenterOf(JComponent container, JComponent content);
}
