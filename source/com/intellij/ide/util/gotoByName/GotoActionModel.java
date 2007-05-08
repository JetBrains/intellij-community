package com.intellij.ide.util.gotoByName;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public class GotoActionModel implements ChooseByNameModel {
  private final Project myProject;
  private static final EmptyIcon EMPTY_ICON = new EmptyIcon(18, 18);

  public GotoActionModel(Project project) {
    myProject = project;
  }

  public String getPromptText() {
    return IdeBundle.message("prompt.gotoaction.enter.action");
  }

  public String getCheckBoxName() {
    return IdeBundle.message("checkbox.other.included");
  }

  public char getCheckBoxMnemonic() {
    return 'd';
  }

  public String getNotInMessage() {
    return IdeBundle.message("label.no.menu.actions.found");
  }

  public String getNotFoundMessage() {
    return IdeBundle.message("label.no.actions.found");
  }

  public boolean loadInitialCheckBoxState() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    return propertiesComponent.isTrueValue("GoToAction.allIncluded");
  }

  public void saveInitialCheckBoxState(boolean state) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    propertiesComponent.setValue("GoToAction.allIncluded", Boolean.toString(state));
  }

  public ListCellRenderer getListCellRenderer() {
    return new DefaultListCellRenderer() {

      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index, final boolean isSelected, final boolean cellHasFocus) {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        final Color bg = isSelected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground();
        panel.setBackground(bg);
        final Color fg = isSelected ? UIUtil.getListSelectionForeground() : UIUtil.getListForeground();

        if (value instanceof Map.Entry) {

          final Map.Entry actionWithParentGroup = (Map.Entry)value;

          final AnAction anAction = (AnAction)actionWithParentGroup.getKey();
          final Presentation templatePresentation = anAction.getTemplatePresentation();
          final Icon icon = templatePresentation.getIcon();
          final LayeredIcon layeredIcon = new LayeredIcon(2);
          layeredIcon.setIcon(EMPTY_ICON, 0);
          if (icon != null){
            layeredIcon.setIcon(icon, 1, (- icon.getIconWidth() + EMPTY_ICON.getIconWidth())/2, (EMPTY_ICON.getIconHeight() - icon.getIconHeight())/2);
          }
          final Shortcut[] shortcutSet = KeymapManager.getInstance().getActiveKeymap().getShortcuts(ActionManager.getInstance().getId(anAction));
          final String actionPresentation = templatePresentation.getText() + (shortcutSet != null && shortcutSet.length > 0
                                                                              ? " (" + KeymapUtil.getShortcutText(shortcutSet[0]) + ")"
                                                                              : "");
          final JLabel actionLabel = new JLabel(actionPresentation, layeredIcon, SwingConstants.LEFT);
          actionLabel.setBackground(bg);
          actionLabel.setForeground(fg);

          panel.add(actionLabel, BorderLayout.WEST);

          final String groupName = (String)actionWithParentGroup.getValue();
          if (groupName != null) {
            final JLabel groupLabel = new JLabel(groupName);
            groupLabel.setBackground(bg);
            groupLabel.setForeground(fg);
            panel.add(groupLabel, BorderLayout.EAST);
          }
        }
        return panel;
      }
    };
  }

  public String[] getNames(boolean checkBoxState) {
    final ArrayList<String> result = new ArrayList<String>();
    final ActionManager actionManager = ActionManager.getInstance();
    collectActionNames(null, result, (ActionGroup)actionManager.getActionOrStub(IdeActions.GROUP_MAIN_MENU));
    if (checkBoxState) {
      final Set<String> ids = ((ActionManagerImpl)actionManager).getActionIds();
      for (String id : ids) {
        final AnAction anAction = actionManager.getAction(id);
        if (!(anAction instanceof ActionGroup) && ActionsTreeUtil.isActionFiltered(null, true).value(anAction)) {
          result.add(anAction.getTemplatePresentation().getText());
        }
      }
    }
    return result.toArray(new String[result.size()]);
  }

  private static void collectActionNames(String filter, Collection<String> result, ActionGroup group){
    final AnAction[] actions = group.getChildren(null);
    for (AnAction action : actions) {
      if (action instanceof ActionGroup) {
        collectActionNames(filter, result, (ActionGroup)action);
      } else if (ActionsTreeUtil.isActionFiltered(filter, true).value(action)) {
        result.add(action.getTemplatePresentation().getText());
      }
    }
  }

  public Object[] getElementsByName(final String name, final boolean checkBoxState) {
    final Comparator<AnAction> comparator = new Comparator<AnAction>() {
      public int compare(final AnAction o1, final AnAction o2) {
        final String text1 = o1.getTemplatePresentation().getText();
        final String text2 = o2.getTemplatePresentation().getText();
        if (text1 != null && text2 != null) {
          if (Comparing.strEqual(name.toLowerCase(), text1.toLowerCase())) return -1;
          if (Comparing.strEqual(name.toLowerCase(), text2.toLowerCase())) return 1;
          return text1.compareToIgnoreCase(text2);
        }
        return 0;
      }
    };
    final TreeMap<AnAction, String> map = new TreeMap<AnAction, String>(comparator);
    final ActionManager actionManager = ActionManager.getInstance();
    final ActionGroup mainMenu = (ActionGroup)actionManager.getActionOrStub(IdeActions.GROUP_MAIN_MENU);
    collectActions(name, map, mainMenu, mainMenu.getTemplatePresentation().getText());
    final Map.Entry[] entries = map.entrySet().toArray(new Map.Entry[map.size()]);
    final TreeMap<AnAction, ActionGroup> additionalActions = new TreeMap<AnAction, ActionGroup>(comparator);
    if (checkBoxState) {
      final Set<String> ids = ((ActionManagerImpl)actionManager).getActionIds();
      for (AnAction action : map.keySet()) { //do not add already included actions
        ids.remove(actionManager.getId(action));
      }
      for (String id : ids) {
        final AnAction anAction = actionManager.getAction(id);
        if (!(anAction instanceof ActionGroup) && ActionsTreeUtil.isActionFiltered(name, true).value(anAction)) {
          additionalActions.put(anAction, null);
        }
      }
      return ArrayUtil.mergeArrays(entries, additionalActions.entrySet().toArray(new Map.Entry[additionalActions.size()]), Map.Entry.class);
    }
    return entries;
  }

  private static void collectActions(String filter, TreeMap<AnAction, String> result, ActionGroup group, final String containingGroupName){
    final AnAction[] actions = group.getChildren(null);
    for (AnAction action : actions) {
      if (action instanceof ActionGroup) {
        final ActionGroup actionGroup = (ActionGroup)action;
        final String groupName = actionGroup.getTemplatePresentation().getText();
        collectActions(filter, result, actionGroup, groupName != null ? groupName : containingGroupName);
      } else if (ActionsTreeUtil.isActionFiltered(filter, true).value(action)) {
        final String groupName = group.getTemplatePresentation().getText();
        result.put(action, groupName != null ? groupName : containingGroupName);
      }
    }
  }

  @Nullable
  public String getFullName(final Object element) {
    return getElementName(element);
  }

  @NotNull
  public String[] getSeparators() {
    return new String[0];
  }

  public String getElementName(final Object element) {
    if (!(element instanceof Map.Entry)) return null;
    return ((AnAction)((Map.Entry)element).getKey()).getTemplatePresentation().getText();
  }
}