package com.intellij.openapi.keymap.impl.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.impl.EmptyIcon;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.Alarm;
import com.intellij.util.ui.treetable.TreeTable;
import com.intellij.util.ui.treetable.TreeTableCellRenderer;
import com.intellij.util.ui.treetable.TreeTableModel;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;

public class ActionsTree {
  private static final Icon EMPTY_ICON = EmptyIcon.create(18, 18);
  private static final Icon QUICK_LIST_ICON = IconLoader.getIcon("/actions/quickList.png");
  private TreeTable myTreeTable;
  private DefaultMutableTreeNode myRoot;
  private JScrollPane myComponent;
  private Keymap myKeymap;
  private Group myMainGroup = new Group("", null, null);
  private ShortcutColumnCellRenderer myShortcutColumnCellRenderer;

  public ActionsTree() {
    myRoot = new DefaultMutableTreeNode("ROOT");
    myShortcutColumnCellRenderer = new ShortcutColumnCellRenderer();

    myTreeTable = new TreeTable(new MyModel(myRoot)) {
      public TreeTableCellRenderer createTableRenderer(TreeTableModel treeTableModel) {
        TreeTableCellRenderer tableRenderer = super.createTableRenderer(treeTableModel);
        tableRenderer.putClientProperty("JTree.lineStyle", "Angled");
        return tableRenderer;
      }

      public TableCellRenderer getCellRenderer(int row, int column){
        if (convertColumnIndexToModel(column) == 1) {
          return myShortcutColumnCellRenderer;
        }
        return super.getCellRenderer(row, column);
      }
    };

    myTreeTable.getTree().setCellRenderer(new DefaultTreeCellRenderer(){
      public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        setBorderSelectionColor(null);
        Icon icon = null;
        if (value instanceof DefaultMutableTreeNode) {
          Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
          boolean changed;
          if (userObject instanceof Group) {
            Group group = (Group)userObject;
            setText(group.getName());
            Keymap originalKeymap = myKeymap.getParent();
            changed = originalKeymap != null && isGroupChanged(group, originalKeymap, myKeymap);
            icon = expanded ? group.getOpenIcon() : group.getIcon();

            if (icon == null) {
              icon = expanded ? getOpenIcon() : getClosedIcon();
            }


          }
          else if (userObject instanceof String) {
            String actionId = (String)userObject;
            AnAction action = ActionManager.getInstance().getAction(actionId);
            setText(action != null ? action.getTemplatePresentation().getText() : actionId);
            if (action != null) {
              Icon actionIcon = action.getTemplatePresentation().getIcon();
              if (actionIcon != null) {
                icon = actionIcon;
              }
            }
            Keymap originalKeymap = myKeymap.getParent();
            changed = originalKeymap != null && isActionChanged(actionId, originalKeymap, myKeymap);
          }
          else if (userObject instanceof QuickList) {
            QuickList list = (QuickList)userObject;
            icon = QUICK_LIST_ICON;
            setText(list.getDisplayName());
            Keymap originalKeymap = myKeymap.getParent();
            changed = originalKeymap != null && isActionChanged(list.getActionId(), originalKeymap, myKeymap);
          }
          else if (userObject instanceof Separator) {
            // TODO[vova,anton]: beautify
            changed = false;
            setText("-------------");
          }
          else {
            throw new IllegalArgumentException("unknown userObject: " + userObject);
          }

          LayeredIcon layeredIcon = new LayeredIcon(2);
          layeredIcon.setIcon(EMPTY_ICON, 0);
          if (icon != null){
            layeredIcon.setIcon(icon, 1, (- icon.getIconWidth() + EMPTY_ICON.getIconWidth())/2, (EMPTY_ICON.getIconHeight() - icon.getIconHeight())/2);
          }
          setIcon(layeredIcon);

          // Set color

          if(sel){
            setForeground(UIManager.getColor("Tree.selectionForeground"));
          }else{
            if(changed){
              setForeground(Color.BLUE);
            }else{
              setForeground(UIManager.getColor("Tree.foreground"));
            }
          }
        }
        return this;
      }
    });

    myTreeTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myTreeTable.getColumnModel().getColumn(0).setPreferredWidth(200);
    myTreeTable.getColumnModel().getColumn(1).setPreferredWidth(100);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTreeTable);
    myComponent = scrollPane;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public void addListSelectionListener(ListSelectionListener l) {
    myTreeTable.getSelectionModel().addListSelectionListener(l);
  }

  private Object getSelectedObject() {
    int selectedRow = myTreeTable.getSelectedRow();
    if (selectedRow < 0 || selectedRow >= myTreeTable.getRowCount()) return null;

    TreePath selectionPath = myTreeTable.getTree().getPathForRow(selectedRow);
    if (selectionPath == null) return null;
    Object userObject = ((DefaultMutableTreeNode)selectionPath.getLastPathComponent()).getUserObject();
    return userObject;
  }

  public String getSelectedActionId() {
    Object userObject = getSelectedObject();
    if (userObject instanceof String) return (String)userObject;
    if (userObject instanceof QuickList) return ((QuickList)userObject).getActionId();
    return null;
  }

  public QuickList getSelectedQuickList() {
    Object userObject = getSelectedObject();
    if (!(userObject instanceof QuickList)) return null;
    return (QuickList)userObject;
  }

  public void reset(Keymap keymap, final QuickList[] allQuickLists) {
    myKeymap = keymap;

    final PathsKeeper pathsKeeper = new PathsKeeper();
    pathsKeeper.storePaths();

    myRoot.removeAllChildren();

    Project project = (Project)DataManager.getInstance().getDataContext(getComponent()).getData(DataConstants.PROJECT);
    Group mainGroup = ActionsTreeUtil.createMainGroup(project, myKeymap, allQuickLists);

    myRoot = ActionsTreeUtil.createNode(mainGroup);
    myMainGroup = mainGroup;
    MyModel model = (MyModel)myTreeTable.getTree().getModel();
    model.setRoot(myRoot);
    model.nodeStructureChanged(myRoot);

    pathsKeeper.restorePaths();
  }

  public Group getMainGroup() {
    return myMainGroup;
  }

  private class MyModel extends DefaultTreeModel implements TreeTableModel {
    protected MyModel(DefaultMutableTreeNode root) {
      super(root);
    }

    public int getColumnCount() {
      return 2;
    }

    public String getColumnName(int column) {
      switch (column) {
        case 0: return "Action";
        case 1: return "Shortcuts";
      }
      return "";
    }

    public Object getValueAt(Object value, int column) {
      if (!(value instanceof DefaultMutableTreeNode)) {
        return "???";
      }

      if (column == 0) {
        return value;
      }
      else if (column == 1) {
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        if (userObject instanceof QuickList) {
          userObject = ((QuickList)userObject).getActionId();
        }

        if (userObject instanceof String) {
          Shortcut[] shortcuts = myKeymap.getShortcuts((String)userObject);
          return KeymapUtil.getShortcutsText(shortcuts);
        }
        else {
          return "";
        }
      }
      else {
        return "???";
      }
    }

    public Object getChild(Object parent, int index) {
      return ((TreeNode)parent).getChildAt(index);
    }

    public int getChildCount(Object parent) {
      return ((TreeNode)parent).getChildCount();
    }

    public Class getColumnClass(int column) {
      if (column == 0) {
        return TreeTableModel.class;
      }
      else {
        return Object.class;
      }
    }

    public boolean isCellEditable(Object node, int column) {
      return column == 0;
    }

    public void setValueAt(Object aValue, Object node, int column) {
    }
  }


  private boolean isActionChanged(String actionId, Keymap oldKeymap, Keymap newKeymap) {
    Shortcut[] oldShortcuts = oldKeymap.getShortcuts(actionId);
    Shortcut[] newShortcuts = newKeymap.getShortcuts(actionId);
    return !Comparing.equal(oldShortcuts, newShortcuts);
  }

  private boolean isGroupChanged(Group group, Keymap oldKeymap, Keymap newKeymap) {
    ArrayList children = group.getChildren();
    for (int i = 0; i < children.size(); i++) {
      Object child = children.get(i);
      if (child instanceof Group) {
        if (isGroupChanged((Group)child, oldKeymap, newKeymap)) {
          return true;
        }
      }
      else if (child instanceof String) {
        String actionId = (String)child;
        if (isActionChanged(actionId, oldKeymap, newKeymap)) {
          return true;
        }
      }
      else if (child instanceof QuickList){
        String actionId = ((QuickList)child).getActionId();
        if (isActionChanged(actionId, oldKeymap, newKeymap)) {
          return true;
        }
      }
    }
    return false;
  }

  public void selectAction(String actionId) {
    final JTree tree = myTreeTable.getTree();

    String path = myMainGroup.getActionQualifiedPath(actionId);
    if (path == null) {
      return;
    }
    final DefaultMutableTreeNode node = getNodeForPath(path);
    if (node == null) {
      return;
    }
    tree.expandPath(new TreePath(((DefaultMutableTreeNode)node.getParent()).getPath()));

    Alarm alarm = new Alarm();
    alarm.addRequest(new Runnable() {
      public void run() {
        JTree tree = myTreeTable.getTree();
        final DefaultTreeModel treeModel = (DefaultTreeModel)tree.getModel();
        int rowForPath = tree.getRowForPath(new TreePath(treeModel.getPathToRoot(node)));
        myTreeTable.getSelectionModel().setSelectionInterval(rowForPath, rowForPath);

        Rectangle pathBounds = tree.getPathBounds(new TreePath(node.getPath()));
        myTreeTable.scrollRectToVisible(pathBounds);
      }
    }, 100);
  }

  private DefaultMutableTreeNode getNodeForPath(String path) {
    Enumeration enumeration = ((DefaultMutableTreeNode)myTreeTable.getTree().getModel().getRoot()).preorderEnumeration();
    while (enumeration.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)enumeration.nextElement();
      if (Comparing.equal(getPath(node), path)) {
        return node;
      }
    }
    return null;
  }

  private String getPath(DefaultMutableTreeNode node) {
    Object userObject = node.getUserObject();
    if (userObject instanceof String) {
      String actionId = (String)userObject;
      return myMainGroup.getActionQualifiedPath(actionId);
    }
    if (userObject instanceof Group) {
      return ((Group)userObject).getQualifiedPath();
    }
    return null;
  }

  private class PathsKeeper {
    private JTree myTree;
    private ArrayList<String> myPathsToExpand;
    private ArrayList<String> mySelectionPaths;

    public PathsKeeper() {
      myTree = myTreeTable.getTree();
    }

    public void storePaths() {
      myPathsToExpand = new ArrayList<String>();
      mySelectionPaths = new ArrayList<String>();

      DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTree.getModel().getRoot();

      TreePath path = new TreePath(root.getPath());
      if (myTree.isPathSelected(path)){
        mySelectionPaths.add(getPath(root));
      }
      if (myTree.isExpanded(path) || root.getChildCount() == 0){
        myPathsToExpand.add(getPath(root));
        _storePaths(root);
      }
    }

    private void _storePaths(DefaultMutableTreeNode root) {
      ArrayList childNodes = childrenToArray(root);
      for(Iterator iterator = childNodes.iterator(); iterator.hasNext();){
        DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)iterator.next();
        TreePath path = new TreePath(childNode.getPath());
        if (myTree.isPathSelected(path)){
          mySelectionPaths.add(getPath(childNode));
        }
        if (myTree.isExpanded(path) || childNode.getChildCount() == 0){
          myPathsToExpand.add(getPath(childNode));
          _storePaths(childNode);
        }
      }
    }

    public void restorePaths() {
      for(int i = 0; i < myPathsToExpand.size(); i++){
        String path = myPathsToExpand.get(i);
        DefaultMutableTreeNode node = getNodeForPath(path);
        if (node != null){
          myTree.expandPath(new TreePath(node.getPath()));
        }
      }

      Alarm alarm = new Alarm();
      alarm.addRequest(new Runnable() {
        public void run() {
          final DefaultTreeModel treeModel = (DefaultTreeModel)myTree.getModel();
          for(int i = 0; i < mySelectionPaths.size(); i++){
            String path = mySelectionPaths.get(i);
            final DefaultMutableTreeNode node = getNodeForPath(path);
            if (node != null){
              int rowForPath = myTree.getRowForPath(new TreePath(treeModel.getPathToRoot(node)));
              myTreeTable.getSelectionModel().setSelectionInterval(rowForPath, rowForPath);
              // TODO[anton] make selection visible

            }
          }
        }
      }, 100);
    }


    private ArrayList childrenToArray(DefaultMutableTreeNode node) {
      ArrayList arrayList = new ArrayList();
      for(int i = 0; i < node.getChildCount(); i++){
        arrayList.add(node.getChildAt(i));
      }
      return arrayList;
    }
  }

  private class ShortcutColumnCellRenderer implements TableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column){
      String actionId = null;

      TreePath treePath = myTreeTable.getTree().getPathForRow(row);
      if (treePath != null) {
        Object userObject = ((DefaultMutableTreeNode)treePath.getLastPathComponent()).getUserObject();
        if (userObject instanceof QuickList) {
          userObject = ((QuickList)userObject).getActionId();
        }
        if (userObject instanceof String) {
          actionId = (String)userObject;
        }
      }

      JPanel panel = new JPanel(new GridBagLayout());

      if (actionId != null) {
        Color foreground = isSelected ? table.getSelectionForeground() : table.getForeground();

        Shortcut[] shortcuts = myKeymap.getShortcuts(actionId);
        for (int i = 0; i < shortcuts.length; i++) {
          Shortcut shortcut = shortcuts[i];

          String text = KeymapUtil.getShortcutText(shortcut);
          Icon icon = KeymapUtil.getShortcutIcon(shortcut);

          JLabel label = new JLabel();
          label.setForeground(foreground);
          label.setText(text);
          label.setIcon(icon);

          panel.add(label, new GridBagConstraints(i,0,1,1,(i < shortcuts.length - 1 ? 0 : 1),0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,5,0,5), 0,0));
        }
      }

      if (isSelected){
        panel.setBackground(table.getSelectionBackground());
      }
      else{
        panel.setBackground(table.getBackground());
      }
      panel.setBorder(hasFocus ? UIManager.getBorder("Table.focusCellHighlightBorder") : BorderFactory.createEmptyBorder(1,1,1,1));

      return panel;
    }
  }
}
