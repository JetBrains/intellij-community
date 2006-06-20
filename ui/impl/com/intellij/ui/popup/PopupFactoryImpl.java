/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.mock.MockConfirmation;
import com.intellij.ui.popup.tree.TreePopupImpl;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PopupFactoryImpl extends JBPopupFactory implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.popup.PopupFactoryImpl");
  private static final Icon QUICK_LIST_ICON = IconLoader.getIcon("/actions/quickList.png");

  private static final Runnable EMPTY = new Runnable() {
    public void run() {
    }
  };

  public PopupFactoryImpl() {}

  public String getComponentName() {
    return "PopupFactory";
  }

  public void initComponent() {}

  public void disposeComponent() {}

  public ListPopup createConfirmation(String title, final Runnable onYes, int defaultOptionIndex) {
    return createConfirmation(title, "&Yes", "&No", onYes, defaultOptionIndex);
  }

  public ListPopup createConfirmation(String title, final String yesText, String noText, final Runnable onYes, int defaultOptionIndex) {
    return createConfirmation(title, yesText, noText, onYes, EMPTY, defaultOptionIndex);
  }

  public ListPopup createConfirmation(String title, final String yesText, String noText, final Runnable onYes, final Runnable onNo, int defaultOptionIndex) {

      final BaseListPopupStep<String> step = new BaseListPopupStep<String>(title, new String[]{yesText, noText}) {
        public PopupStep onChosen(String selectedValue, final boolean finalChoice) {
          if (selectedValue.equals(yesText)) {
            onYes.run();
          }
          else {
            onNo.run();
          }
          return FINAL_CHOICE;
        }

        public void canceled() {
          onNo.run();
        }

        public boolean isMnemonicsNavigationEnabled() {
          return true;
        }
      };
      step.setDefaultOptionIndex(defaultOptionIndex);

    if (!ApplicationManagerEx.getApplicationEx().isUnitTestMode()) {
      return new ListPopupImpl(step);
    } else {
      return new MockConfirmation(step, yesText);
    }
  }


  public ListPopup createActionGroupPopup(final String title,
                                          ActionGroup actionGroup,
                                          DataContext dataContext,
                                          boolean showNumbers,
                                          boolean showDisabledActions,
                                          boolean honorActionMnemonics) {
    final Component component = (Component)dataContext.getData(DataConstants.CONTEXT_COMPONENT);
    LOG.assertTrue(component != null);

    ListPopupStep<ActionItem> step =
      createActionsStep(actionGroup, dataContext, showNumbers, showDisabledActions, title, component, honorActionMnemonics);

    return new ListPopupImpl(step);
  }

  public ListPopup createActionGroupPopup(String title,
                                          ActionGroup actionGroup,
                                          DataContext dataContext,
                                          ActionSelectionAid selectionAidMethod,
                                          boolean showDisabledActions) {
    return createActionGroupPopup(title, actionGroup, dataContext,
                                  selectionAidMethod == ActionSelectionAid.NUMBERING,
                                  showDisabledActions,
                                  selectionAidMethod == ActionSelectionAid.MNEMONICS);
  }

  public ListPopupStep createActionsStep(final ActionGroup actionGroup,
                                                             final DataContext dataContext,
                                                             final boolean showNumbers,
                                                             final boolean showDisabledActions,
                                                             final String title,
                                                             final Component component,
                                                             final boolean honorActionMnemonics) {
    final ArrayList<ActionItem> items = new ArrayList<ActionItem>();
    fillModel(items, actionGroup, dataContext, showNumbers, showDisabledActions, new HashMap<AnAction, Presentation>(), 0, false, honorActionMnemonics);

    return new ActionPopupStep(items, title, component, showNumbers || honorActionMnemonics && itemsHaveMnemonics(items));
  }

  private static boolean itemsHaveMnemonics(final ArrayList<ActionItem> items) {
    for (ActionItem item : items) {
      if (item.getAction().getTemplatePresentation().getMnemonic() != 0) return true;
    }

    return false;
  }

  public ListPopup createWizardStep(PopupStep step) {
    return new ListPopupImpl(step);
  }

  public TreePopup createTree(JBPopup parent, TreePopupStep aStep, Object parentValue) {
    return new TreePopupImpl(parent, aStep, parentValue);
  }

  public TreePopup createTree(TreePopupStep aStep) {
    return new TreePopupImpl(aStep);
  }

  public ComponentPopupBuilder createComponentPopupBuilder(JComponent content, JComponent prefferableFocusComponent) {
    return new ComponentPopupBuilderImpl(content, prefferableFocusComponent);
  }

 
  public RelativePoint guessBestPopupLocation(DataContext dataContext) {
    KeyboardFocusManager focusManager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
    JComponent focusOwner=(JComponent)focusManager.getFocusOwner();

    if (focusOwner == null) {
      throw new IllegalArgumentException("focusOwner cannot be null");
    }

    final Rectangle visibleRect = focusOwner.getVisibleRect();

    Point popupMenuPoint = null;

    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor != null && focusOwner == editor.getContentComponent()) {
      return guessBestPopupLocation(editor);
    }
    else if (focusOwner instanceof JList) { // JList
      JList list = (JList)focusOwner;
      int firstVisibleIndex = list.getFirstVisibleIndex();
      int lastVisibleIndex = list.getLastVisibleIndex();
      int[] selectedIndices = list.getSelectedIndices();
      for (int index : selectedIndices) {
        if (firstVisibleIndex <= index && index <= lastVisibleIndex) {
          Rectangle cellBounds = list.getCellBounds(index, index);
          popupMenuPoint = new Point(visibleRect.x + visibleRect.width / 4, cellBounds.y + cellBounds.height);
          break;
        }
      }
    }
    else if (focusOwner instanceof JTree) { // JTree
      JTree tree = (JTree)focusOwner;
      int[] selectionRows = tree.getSelectionRows();
      for (int i = 0; selectionRows != null && i < selectionRows.length; i++) {
        int row = selectionRows[i];
        Rectangle rowBounds = tree.getRowBounds(row);
        if (visibleRect.y <= rowBounds.y && rowBounds.y <= visibleRect.y + visibleRect.height) {
          popupMenuPoint = new Point(visibleRect.x + visibleRect.width / 4, rowBounds.y + rowBounds.height);
          break;
        }
      }
    } else if (focusOwner instanceof PopupOwner){
      popupMenuPoint = ((PopupOwner)focusOwner).getBestPopupPosition();
    }
    // TODO[vova] add usability for JTable
    if (popupMenuPoint == null) {
      popupMenuPoint = new Point(visibleRect.x + visibleRect.width / 2, visibleRect.y + visibleRect.height / 2);
    }

    return new RelativePoint(focusOwner, popupMenuPoint);
  }

  public RelativePoint guessBestPopupLocation(Editor editor) {
    VisualPosition logicalPosition = editor.getCaretModel().getVisualPosition();
    Point p = editor.visualPositionToXY(new VisualPosition(logicalPosition.line + 1, logicalPosition.column));

    final Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    if (!visibleArea.contains(p)) {
      p = new Point((visibleArea.x + visibleArea.width) / 2, (visibleArea.y + visibleArea.height) / 2);
    }

    return new RelativePoint(editor.getContentComponent(), p);
  }

  public Point getCenterOf(JComponent container, JComponent content) {
    return JBPopupImpl.getCenterOf(container, content);
  }

  private static int fillModel(List<ActionItem> listModel,
                               ActionGroup actionGroup,
                               DataContext dataContext,
                               boolean showNumbers,
                               boolean showDisabled,
                               HashMap<AnAction, Presentation> action2presentation,
                               int startNumberingFrom, boolean prependWithSeparator,
                               final boolean honorActionMnemonics) {
    int n = startNumberingFrom;
    AnAction[] actions = actionGroup.getChildren(new AnActionEvent(null,
                                                                   dataContext,
                                                                   ActionPlaces.UNKNOWN,
                                                                   getPresentation(actionGroup, action2presentation),
                                                                   ActionManager.getInstance(),
                                                                   0));
    int maxWidth = -1;
    int maxHeight = -1;
    for (AnAction action : actions) {
      Icon icon = action.getTemplatePresentation().getIcon();
      if (icon != null) {
        final int width = icon.getIconWidth();
        final int height = icon.getIconHeight();
        if (maxWidth < width) {
          maxWidth = width;
        }
        if (maxHeight < height) {
          maxHeight = height;
        }
      }
    }
    Icon emptyIcon = maxHeight != -1 && maxWidth != -1 ? new EmptyIcon(maxWidth, maxHeight) : null;

    for (AnAction action : actions) {
      if (action instanceof Separator) {
        prependWithSeparator = true;
      }
      else {
        if (action instanceof ActionGroup) {

          ActionGroup group = (ActionGroup)action;
          n = group.isPopup()
              ? appendAction(group, action2presentation, dataContext, showDisabled, showNumbers, n, listModel, emptyIcon,
                             prependWithSeparator, honorActionMnemonics)
              : fillModel(listModel, group, dataContext, showNumbers, showDisabled, action2presentation, n, prependWithSeparator, honorActionMnemonics);
        }
        else {
          n = appendAction(action, action2presentation, dataContext, showDisabled, showNumbers, n, listModel, emptyIcon,
                           prependWithSeparator, honorActionMnemonics);
        }
        prependWithSeparator = false;
      }
    }

    return n;
  }

  private static int appendAction(AnAction action,
                                  HashMap<AnAction, Presentation> action2presentation,
                                  DataContext dataContext,
                                  boolean showDisabled,
                                  boolean showNumbers,
                                  int n,
                                  List<ActionItem> listModel,
                                  Icon emptyIcon,
                                  final boolean prependWithSeparator,
                                  final boolean honorActionMnemonics) {
    Presentation presentation = getPresentation(action, action2presentation);
    AnActionEvent event = new AnActionEvent(null,
                                            dataContext,
                                            ActionPlaces.UNKNOWN,
                                            presentation,
                                            ActionManager.getInstance(),
                                            0);

    action.beforeActionPerformedUpdate(event);
    if ((showDisabled || presentation.isEnabled()) && presentation.isVisible()) {
      String text = presentation.getText();
      if (showNumbers) {
        if (n < 9) {
          text = "&" + (n + 1) + ". " + text;
        }
        else if (n == 9) {
          text = "&" + 0 + ". " + text;
        }
        else {
          text = "&" + (char)('A' + n - 10) + ". " + text;
        }
        n++;
      }
      else if (honorActionMnemonics) {
        text = Presentation.restoreTextWithMnemonic(text, action.getTemplatePresentation().getMnemonic());
      }

      Icon icon = presentation.getIcon();
      if (icon == null) {
        final String actionId = ActionManager.getInstance().getId(action);
        if (actionId != null && actionId.startsWith("QuickList.")){
          icon = QUICK_LIST_ICON;
        }
        else {
          icon = emptyIcon;
        }

      }
      listModel.add(new ActionItem(action, text, presentation.isEnabled(), icon, prependWithSeparator));
    }
    return n;
  }

  private static Presentation getPresentation(AnAction action, Map<AnAction, Presentation> action2presentation) {
    Presentation presentation = action2presentation.get(action);
    if (presentation == null) {
      presentation = (Presentation)action.getTemplatePresentation().clone();
      action2presentation.put(action, presentation);
    }
    return presentation;
  }

  private static class ActionItem {
    private AnAction myAction;
    private String myText;
    private boolean myIsEnabled;
    private Icon myIcon;
    private boolean myPrependWithSeparator;

    public ActionItem(AnAction action, String text, boolean enabled, Icon icon, final boolean prependWithSeparator) {
      myAction = action;
      myText = text;
      myIsEnabled = enabled;
      myIcon = icon;
      myPrependWithSeparator = prependWithSeparator;
    }

    public AnAction getAction() {
      return myAction;
    }

    public String getText() {
      return myText;
    }

    public Icon getIcon() {
      return myIcon;
    }

    public boolean isPrependWithSeparator() {
      return myPrependWithSeparator;
    }

    public boolean isEnabled() { return myIsEnabled; }
  }

  private static class ActionPopupStep implements ListPopupStep<ActionItem>, MnemonicNavigationFilter<ActionItem>, SpeedSearchFilter<ActionItem> {
    private final ArrayList<ActionItem> myItems;
    private final String myTitle;
    private Component myContext;
    private boolean myEnableMnemonics;

    public ActionPopupStep(final ArrayList<ActionItem> items, final String title, Component context, boolean enableMnemonics) {
      myItems = items;
      myTitle = title;
      myContext = context;
      myEnableMnemonics = enableMnemonics;
    }

    public List<ActionItem> getValues() {
      return myItems;
    }

    public boolean isSelectable(final ActionItem value) {
      return value.isEnabled();
    }

    public int getMnemonicPos(final ActionItem value) {
      final String text = getTextFor(value);
      int i = text.indexOf(UIUtil.MNEMONIC);
      if (i < 0) {
        i = text.indexOf('&');
      }
      if (i < 0) {
        i = text.indexOf('_');
      }
      return i;
    }

    public Icon getIconFor(final ActionItem aValue) {
      return aValue.getIcon();
    }

    @NotNull
    public String getTextFor(final ActionItem value) {
      return value.getText();
    }

    public ListSeparator getSeparatorAbove(final ActionItem value) {
      return value.isPrependWithSeparator() ? new ListSeparator() : null;
    }

    public int getDefaultOptionIndex() {
      return 0;
    }

    public String getTitle() {
      return myTitle;
    }

    public PopupStep onChosen(final ActionItem actionChoice, final boolean finalChoice) {
      if (!actionChoice.isEnabled()) return PopupStep.FINAL_CHOICE;
      final AnAction action = actionChoice.getAction();
      final DataContext dataContext = DataManager.getInstance().getDataContext(myContext);
      if (action instanceof ActionGroup) {
        return JBPopupFactory.getInstance().createActionsStep((ActionGroup)action, dataContext, myEnableMnemonics, false, null, myContext, false);
      }
      else {
        // invokeLater is required to get a chance for the popup to hide in case the action called displays modal dialog
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            action.actionPerformed(new AnActionEvent(null,
                                                     dataContext,
                                                     ActionPlaces.UNKNOWN,
                                                     (Presentation)action.getTemplatePresentation().clone(),
                                                     ActionManager.getInstance(),
                                                     0));
          }
        });
        return PopupStep.FINAL_CHOICE;
      }
    }

    public boolean hasSubstep(final ActionItem selectedValue) {
      return selectedValue.isEnabled() && selectedValue.getAction() instanceof ActionGroup;
    }

    public void canceled() {
    }

    public boolean isMnemonicsNavigationEnabled() {
      return myEnableMnemonics;
    }

    public MnemonicNavigationFilter<ActionItem> getMnemonicNavigationFilter() {
      return this;
    }

    public boolean canBeHidden(final ActionItem value) {
      return true;
    }

    public String getIndexedString(final ActionItem value) {
      return getTextFor(value);
    }

    public boolean isSpeedSearchEnabled() {
      return !myEnableMnemonics;
    }

    public boolean isAutoSelectionEnabled() {
      return false;
    }

    public SpeedSearchFilter<ActionItem> getSpeedSearchFilter() {
      return this;
    }
  }
}
