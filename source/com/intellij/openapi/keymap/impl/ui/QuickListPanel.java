package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.impl.EmptyIcon;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public class QuickListPanel {
  private static final Icon EMPTY_ICON = EmptyIcon.create(16, 16);

  private JButton myRemoveActionButton;
  private JButton myIncludeActionButton;
  private JButton myMoveActionDownButton;
  private JButton myMoveActionUpButton;
  private JPanel myPanel;
  private JTree myActionsTree;
  private JList myActionsList;
  private JTextField myDisplayName;
  private JTextField myDescription;
  private JButton myAddSeparatorButton;

  public QuickListPanel(QuickList origin, final QuickList[] allQuickLists, Project project) {
    ActionsTreeUtil.Group rootGroup = ActionsTreeUtil.createMainGroup(project, null, allQuickLists);
    DefaultMutableTreeNode root = ActionsTreeUtil.createNode(rootGroup);
    DefaultTreeModel model = new DefaultTreeModel(root);
    myActionsTree.setModel(model);
    myActionsTree.setCellRenderer(new MyTreeCellRenderer());

    myActionsList.setModel(new DefaultListModel());
    myActionsList.setCellRenderer(new MyListCellRenderer());

    myActionsTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        update();
      }
    });

    myActionsTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
          includeSelectedAction();
        }
      }
    });

    myActionsList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
          excludeSelectionAction();
        }
      }
    });

    myActionsList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        update();
      }
    });

    myActionsTree.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        includeSelectedAction();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myIncludeActionButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        includeSelectedAction();
      }
    });

    myAddSeparatorButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        addSeparator();
      }
    });

    myActionsList.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        excludeSelectionAction();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myRemoveActionButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        excludeSelectionAction();
      }
    });

    myMoveActionUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int idx = myActionsList.getSelectedIndex();
        if (idx > 0) {
          DefaultListModel listModel = (DefaultListModel)myActionsList.getModel();
          Object oldValue = listModel.get(idx);
          listModel.removeElementAt(idx);
          listModel.add(--idx, oldValue);
          myActionsList.getSelectionModel().setSelectionInterval(idx, idx);
        }
      }
    });

    myMoveActionDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int idx = myActionsList.getSelectedIndex();
        DefaultListModel listModel = (DefaultListModel)myActionsList.getModel();
        if (idx < listModel.getSize() - 1) {
          Object oldValue = listModel.get(idx);
          listModel.removeElementAt(idx);
          listModel.add(++idx, oldValue);
          myActionsList.getSelectionModel().setSelectionInterval(idx, idx);
        }
      }
    });

    myDisplayName.setText(origin.getDisplayName());
    myDescription.setText(origin.getDescription());

    String[] ids = origin.getActionIds();
    for (int i = 0; i < ids.length; i++) {
      includeActionId(ids[i]);
    }

    update();
  }

  private void excludeSelectionAction() {
    Object[] ids = myActionsList.getSelectedValues();
    for (int i = 0; i < ids.length; i++) {
      ((DefaultListModel)myActionsList.getModel()).removeElement(ids[i]);
    }
    update();
  }

  private void includeSelectedAction() {
    String[] ids = getTreeSelectedActionIds();
    for (int i = 0; i < ids.length; i++) {
      includeActionId(ids[i]);
    }
    DefaultListModel listModel = (DefaultListModel)myActionsList.getModel();
    int size = listModel.getSize();
    ListSelectionModel selectionModel = myActionsList.getSelectionModel();
    if (size > 0) {
      selectionModel.removeIndexInterval(0, size - 1);
    }
    for (int i = 0; i < ids.length; i++) {
      int idx = listModel.lastIndexOf(ids[i]);
      if (idx >= 0) {
        selectionModel.addSelectionInterval(idx, idx);
      }
    }
    update();
  }

  private void addSeparator() {
    DefaultListModel model = (DefaultListModel)myActionsList.getModel();
    model.addElement(QuickList.SEPARATOR_ID);
    update();
  }

  public JList getActionsList() {
    return myActionsList;
  }

  public String getDescription() {
    return myDescription.getText();
  }

  public String getDisplayName() {
    return myDisplayName.getText();
  }

  private void update() {
    myIncludeActionButton.setEnabled(getTreeSelectedActionIds().length > 0);
    myRemoveActionButton.setEnabled(myActionsList.getSelectedValues().length > 0);
    boolean enableMove = myActionsList.getSelectedValues().length == 1;
    myMoveActionUpButton.setEnabled(enableMove && myActionsList.getSelectedIndex() > 0);
    myMoveActionDownButton.setEnabled(enableMove && myActionsList.getSelectedIndex() < myActionsList.getModel().getSize() - 1);
  }

  private void includeActionId(String id) {
    DefaultListModel model = (DefaultListModel)myActionsList.getModel();
    if (!QuickList.SEPARATOR_ID.equals(id) && model.contains(id)) return;
    model.addElement(id);
  }

  private String[] getTreeSelectedActionIds() {
    TreePath[] paths = myActionsTree.getSelectionPaths();
    if (paths == null) return ArrayUtil.EMPTY_STRING_ARRAY;

    ArrayList<String> actions = new ArrayList<String>();
    for (int i = 0; i < paths.length; i++) {
      Object node = paths[i].getLastPathComponent();
      if (node instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode defNode = (DefaultMutableTreeNode)node;
        Object userObject = defNode.getUserObject();
        if (userObject instanceof String) {
          actions.add((String)userObject);
        }
        else if (userObject instanceof QuickList) {
          actions.add(((QuickList)userObject).getActionId());
        }
      }
    }
    return actions.toArray(new String[actions.size()]);
  }

  public JPanel getPanel() {
    return myPanel;
  }

  private class MyTreeCellRenderer extends DefaultTreeCellRenderer {
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean sel,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      if (value instanceof DefaultMutableTreeNode) {
        boolean used = false;
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        if (userObject instanceof ActionsTreeUtil.Group) {
          ActionsTreeUtil.Group group = (ActionsTreeUtil.Group)userObject;
          setText(group.getName());
          Icon icon = expanded ? group.getOpenIcon() : group.getIcon();

          if (icon == null) {
            icon = expanded ? getOpenIcon() : getClosedIcon();
          }

          setIcon(icon);
        }
        else if (userObject instanceof String) {
          String actionId = (String)userObject;
          used = ((DefaultListModel)myActionsList.getModel()).lastIndexOf(actionId) >= 0;
          AnAction action = ActionManager.getInstance().getAction(actionId);
          setText(action != null ? action.getTemplatePresentation().getText() : actionId);
          Icon icon = EMPTY_ICON;
          if (action != null) {
            Icon actionIcon = action.getTemplatePresentation().getIcon();
            if (actionIcon != null) {
              icon = actionIcon;
            }
          }
          setIcon(icon);
        }
        else if (userObject instanceof QuickList) {
          QuickList list = (QuickList)userObject;
          setIcon(EMPTY_ICON);
          setText(list.getDisplayName());
          used = ((DefaultListModel)myActionsList.getModel()).lastIndexOf(list.getActionId()) >= 0;
        }
        else if (userObject instanceof Separator) {
          // TODO[vova,anton]: beautify
          setText("-------------");
          setIcon(EMPTY_ICON);
        }
        else {
          throw new IllegalArgumentException("unknown userObject: " + userObject);
        }

        if (sel) {
          setForeground(UIManager.getColor("Tree.selectionForeground"));
        }
        else {
          Color foreground = used ? UIManager.getColor("textInactiveText") : UIManager.getColor("Tree.foreground");
          setForeground(foreground);
        }
      }
      return this;
    }
  }

  private static class MyListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      String actionId = (String)value;
      if (QuickList.SEPARATOR_ID.equals(actionId)) {
        // TODO[vova,anton]: beautify
        setText("-------------");
        setIcon(EMPTY_ICON);
      }
      else {
        AnAction action = ActionManager.getInstance().getAction(actionId);
        setText(action != null ? action.getTemplatePresentation().getText() : actionId);
        Icon icon = EMPTY_ICON;
        if (action != null) {
          Icon actionIcon = action.getTemplatePresentation().getIcon();
          if (actionIcon != null) {
            icon = actionIcon;
          }
        }
        setIcon(icon);
      }
      return this;
    }
  }
}