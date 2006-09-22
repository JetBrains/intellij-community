package com.intellij.openapi.ui.popup;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Factory class for creating popup chooser windows (similar to the Code | Generate... popup).
 *
 * @author mike
 */
public abstract class JBPopupFactory {
  /**
   * Returns the popup factory instance.
   *
   * @return the popup factory instance.
   */
  public static JBPopupFactory getInstance() {
    return ApplicationManager.getApplication().getComponent(JBPopupFactory.class);
  }

  public PopupChooserBuilder createListPopupBuilder(JList list) {
    return new PopupChooserBuilder(list);
  }

  /**
   * Creates a popup with the specified title and two options, Yes and No.
   *
   * @param title the title of the popup.
   * @param onYes the runnable which is executed when the Yes option is selected.
   * @param defaultOptionIndex the index of the option which is selected by default.
   * @return the popup instance.
   */
  public abstract ListPopup createConfirmation(String title, Runnable onYes, int defaultOptionIndex);

  /**
   * Creates a popup allowing to choose one of two specified options and execute code when one of them is selected.
   *
   * @param title the title of the popup.
   * @param yesText the title for the Yes option.
   * @param noText the title for the No option.
   * @param onYes the runnable which is executed when the Yes option is selected.
   * @param defaultOptionIndex the index of the option which is selected by default.
   * @return the popup instance.
   */
  public abstract ListPopup createConfirmation(String title, String yesText, String noText, Runnable onYes, int defaultOptionIndex);

  /**
   * Creates a popup allowing to choose one of two specified options and execute code when either of them is selected.
   *
   * @param title the title of the popup.
   * @param yesText the title for the Yes option.
   * @param noText the title for the No option.
   * @param onYes the runnable which is executed when the Yes option is selected.
   * @param onNo the runnable which is executed when the No option is selected.
   * @param defaultOptionIndex the index of the option which is selected by default.
   * @return the popup instance.
   */
  public abstract ListPopup createConfirmation(String title, String yesText, String noText, Runnable onYes, Runnable onNo, int defaultOptionIndex);

  /**
   * @deprecated use {@link #createActionGroupPopup(String, com.intellij.openapi.actionSystem.ActionGroup, com.intellij.openapi.actionSystem.DataContext, com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid, boolean)} instead
   */
  public abstract ListPopupStep createActionsStep(ActionGroup actionGroup,
                                                  DataContext dataContext,
                                                  boolean showNumbers,
                                                  boolean showDisabledActions,
                                                  String title,
                                                  Component component,
                                                  boolean honorActionMnemonics);

  /**
   * @deprecated use {@link #createActionGroupPopup(String, com.intellij.openapi.actionSystem.ActionGroup, com.intellij.openapi.actionSystem.DataContext, com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid, boolean)} instead
   */
  public abstract ListPopupStep createActionsStep(ActionGroup actionGroup,
                                                  DataContext dataContext,
                                                  boolean showNumbers,
                                                  boolean showDisabledActions,
                                                  String title,
                                                  Component component,
                                                  boolean honorActionMnemonics,
                                                  int defaultOptionIndex);

  /**
   * Possible ways to select actions in a popup from keyboard.
   */
  public enum ActionSelectionAid {
    /**
     * The actions in a popup are prefixed by numbers (indexes in the list).
     */
    NUMBERING,

    /**
     * The actions in a popup can be selected by typing part of the action's text.
     */
    SPEEDSEARCH,

    /**
     * The actions in a popup can be selected by pressing the character from the action's text prefixed with
     * an &amp; character.
     */
    MNEMONICS
  }

  /**
   * Creates a popup allowing to choose one of the actions from the specified action group.
   *
   * @param title the title of the popup.
   * @param actionGroup the action group from which the popup is built.
   * @param dataContext the data context which provides the data for the selected action
   * @param selectionAidMethod keyboard selection mode for actions in the popup.
   * @param showDisabledActions if true, disabled actions are shown as disabled; if false, disabled actions are not shown
   * @return the popup instance.
   */
  public abstract ListPopup createActionGroupPopup(String title,
                                                   ActionGroup actionGroup,
                                                   DataContext dataContext,
                                                   ActionSelectionAid selectionAidMethod,
                                                   boolean showDisabledActions);

  /**
   * Creates a popup allowing to choose one of the actions from the specified action group.
   *
   * @param title the title of the popup.
   * @param actionGroup the action group from which the popup is built.
   * @param dataContext the data context which provides the data for the selected action
   * @param selectionAidMethod keyboard selection mode for actions in the popup.
   * @param showDisabledActions if true, disabled actions are shown as disabled; if false, disabled actions are not shown
   * @param disposeCallback method which is called when the popup is closed (either by selecting an action or by canceling)
   * @param maxRowCount maximum number of popup rows visible at once (if there are more actions in the action group, a scrollbar
   *                    is displayed)
   * @return the popup instance.
   */
  public abstract ListPopup createActionGroupPopup(String title,
                                                   ActionGroup actionGroup,
                                                   DataContext dataContext,
                                                   ActionSelectionAid selectionAidMethod,
                                                   boolean showDisabledActions,
                                                   @Nullable Runnable disposeCallback,
                                                   int maxRowCount);


  /**
   * Creates a custom popup with the specified step.
   *
   * @param step the custom step for the popup.
   * @return the popup instance.
   */
  public abstract ListPopup createWizardStep(PopupStep step);

  public abstract TreePopup createTree(JBPopup parent, TreePopupStep step, Object parentValue);
  public abstract TreePopup createTree(TreePopupStep step);

  public abstract ComponentPopupBuilder createComponentPopupBuilder(JComponent content, JComponent prefferableFocusComponent);

  /**
   * Returns the location where a popup with the specified data context is displayed.
   *
   * @param dataContext the data context from which the location is determined.
   * @return location as close as possible to the action origin. Method has special handling of
   *         the following components:<br>
   *         - caret offset for editor<br>
   *         - current selected node for tree<br>
   *         - current selected row for list<br>
   */
  public abstract RelativePoint guessBestPopupLocation(DataContext dataContext);

  /**
   * Returns the location where a popup invoked from the specified editor should be displayed.
   *
   * @param editor the editor over which the popup is shown.
   * @return location as close as possible to the action origin.
   */
  public abstract RelativePoint guessBestPopupLocation(Editor editor);

  public abstract Point getCenterOf(JComponent container, JComponent content);
}
