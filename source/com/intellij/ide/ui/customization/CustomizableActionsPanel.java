package com.intellij.ide.ui.customization;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.impl.EmptyIcon;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrame;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ReorderableListController;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * User: anna
 * Date: Mar 17, 2005
 */
public class CustomizableActionsPanel {
  private static final Icon EMPTY_ICON = EmptyIcon.create(16, 16);

  private JButton myRemoveActionButton;
  private JButton myAddActionButton;
  private JButton myMoveActionDownButton;
  private JButton myMoveActionUpButton;
  private JPanel myPanel;
  private JTree myActionsTree;
  private JTextField myDescription;
  private JButton myAddSeparatorButton;


  private CustomActionsSchema mySelectedSchema;

  private DefaultListModel myCustomizationSchemas = new DefaultListModel();

  private JList myList = new JList();
  private JTextField myName;
  private JPanel myListPane;

  public CustomizableActionsPanel() {
    myList.setModel(myCustomizationSchemas);
    myList.setPrototypeCellValue(new CustomActionsSchema("xxxxxxxxxxxxx", ""));
    fillSchemaList();
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(
        JList list,
        Object value,
        int index,
        boolean isSelected,
        boolean cellHasFocus) {
        final Component listCellRendererComponent = super.getListCellRendererComponent(list, value, index, isSelected,
                                                                                       cellHasFocus);
        if (value instanceof CustomActionsSchema){
          setText(((CustomActionsSchema)value).getName());
        }
        return listCellRendererComponent;
      }
    });
    Group rootGroup = new Group("root", null, null);
    DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootGroup);
    DefaultTreeModel model = new DefaultTreeModel(root);
    myActionsTree.setModel(model);

    myActionsTree.setRootVisible(false);
    myActionsTree.setShowsRootHandles(true);
    myActionsTree.putClientProperty("JTree.lineStyle", "Angled");
    myActionsTree.setCellRenderer(new MyTreeCellRenderer());

    setButtonsDisabled();
   
    myActionsTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath[] selectionPaths = myActionsTree.getSelectionPaths();
        myAddActionButton.setEnabled(selectionPaths != null && selectionPaths.length == 1);
        myAddSeparatorButton.setEnabled(selectionPaths != null && selectionPaths.length == 1);
        myRemoveActionButton.setEnabled(selectionPaths != null);
        if (selectionPaths != null) {
          for (int i = 0; i < selectionPaths.length; i++) {
            TreePath selectionPath = selectionPaths[i];
            if (selectionPath.getPath() != null && selectionPath.getPath().length <= 2){
              setButtonsDisabled();
              break;
            }
          }
        }
        myMoveActionUpButton.setEnabled(isMoveSupported(myActionsTree, -1));
        myMoveActionDownButton.setEnabled(isMoveSupported(myActionsTree, 1));
      }
    });

    myAddActionButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final ArrayList<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath selectionPath = myActionsTree.getLeadSelectionPath();
        if (selectionPath != null){
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          final FindAvailableActionsDialog dlg = new FindAvailableActionsDialog();
          dlg.show();
          if (dlg.isOK()){
            final Set<Object> toAdd = dlg.getTreeSelectedActionIds();
            for (Iterator<Object> iterator = toAdd.iterator(); iterator.hasNext();) {
              final Object o = iterator.next();
              final ActionUrl url = new ActionUrl(ActionUrl.getGroupPath(new TreePath(node.getPath())), o, ActionUrl.ADDED, node.getParent().getIndex(node) + 1);
              mySelectedSchema.addAction(url);
              ActionUrl.changePathInActionsTree(myActionsTree, url);
            }
            ((DefaultTreeModel)myActionsTree.getModel()).reload();
          }
        }
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      }
    });

    myAddSeparatorButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final ArrayList<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath selectionPath = myActionsTree.getLeadSelectionPath();
        if (selectionPath != null){
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          final ActionUrl url = new ActionUrl(ActionUrl.getGroupPath(selectionPath), Separator.getInstance(), ActionUrl.ADDED, node.getParent().getIndex(node) + 1);
         ActionUrl.changePathInActionsTree(myActionsTree, url);
          mySelectedSchema.addAction(url);
          ((DefaultTreeModel)myActionsTree.getModel()).reload();
        }
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      }
    });


    myRemoveActionButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final ArrayList<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath[] selectionPath = myActionsTree.getSelectionPaths();
        if (selectionPath != null){
          for (int i = 0; i < selectionPath.length; i++) {
            TreePath treePath = selectionPath[i];
            final ActionUrl url = CustomizationUtil.getActionUrl(treePath, ActionUrl.DELETED);
            ActionUrl.changePathInActionsTree(myActionsTree, url);
            mySelectedSchema.addAction(url);
          }
          ((DefaultTreeModel)myActionsTree.getModel()).reload();
        }
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      }
    });

    myMoveActionUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final ArrayList<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath[] selectionPath = myActionsTree.getSelectionPaths();
        if (selectionPath != null){
          for (int i = 0; i < selectionPath.length; i++) {
            TreePath treePath = selectionPath[i];
            final ActionUrl url = CustomizationUtil.getActionUrl(treePath, ActionUrl.MOVE);
            final int absolutePosition = url.getAbsolutePosition();
            url.setInitialPosition(absolutePosition);
            url.setAbsolutePosition(absolutePosition - 1);
            ActionUrl.changePathInActionsTree(myActionsTree, url);
            mySelectedSchema.addAction(url);
          }
          ((DefaultTreeModel)myActionsTree.getModel()).reload();
        }
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      }
    });

    myMoveActionDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final ArrayList<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath[] selectionPath = myActionsTree.getSelectionPaths();
        if (selectionPath != null){
          for (int i = 0; i < selectionPath.length; i++) {
            TreePath treePath = selectionPath[i];
            final ActionUrl url = CustomizationUtil.getActionUrl(treePath, ActionUrl.MOVE);
            final int absolutePosition = url.getAbsolutePosition();
            url.setInitialPosition(absolutePosition);
            url.setAbsolutePosition(absolutePosition + 1);
            ActionUrl.changePathInActionsTree(myActionsTree, url);
            mySelectedSchema.addAction(url);
          }
          ((DefaultTreeModel)myActionsTree.getModel()).reload();
        }
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      }
    });

    myDescription.setText(mySelectedSchema != null ? mySelectedSchema.getDescription() : "");

    final DefaultActionGroup group = new DefaultActionGroup();
    ReorderableListController<CustomActionsSchema> controller = ReorderableListController.create(myList, group);
    controller.addAddAction("Add customization schema", new Factory<CustomActionsSchema>() {
      public CustomActionsSchema create() {
        return new CustomActionsSchema(createUniqueName(), "");
      }
    }, true);

    controller.addRemoveAction("Remove customization schema");
    controller.addCopyAction("Copy schema", new Convertor<CustomActionsSchema, CustomActionsSchema>() {
      public CustomActionsSchema convert(final CustomActionsSchema o) {
        final CustomActionsSchema customActionsSchema = o.copyFrom();
        customActionsSchema.setName(createUniqueName());
        return customActionsSchema;
      }
    }, new Condition<CustomActionsSchema>() {
      public boolean value(final CustomActionsSchema object) {
        return true;
      }
    });
    myListPane.setLayout(new BorderLayout());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    myListPane.add(toolbar.getComponent(), BorderLayout.NORTH);
    myListPane.add(ScrollPaneFactory.createScrollPane(myList), BorderLayout.CENTER);
    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final CustomActionsSchema selectedValue = (CustomActionsSchema)myList.getSelectedValue();
        if (selectedValue != null){
          if (mySelectedSchema != null){
            //CustomizationUtil.optimizeSchema(myActionsTree, mySelectedSchema);
          }
          mySelectedSchema = selectedValue;
          setNameAndDescription(true, mySelectedSchema.getName(), mySelectedSchema.getDescription());
          patchActionsTreeCorrespondingToSchema((DefaultMutableTreeNode)myActionsTree.getModel().getRoot());
        } else {
          mySelectedSchema = null;
          ((DefaultMutableTreeNode)myActionsTree.getModel().getRoot()).removeAllChildren();
        }
      }
    });
    myList.getModel().addListDataListener(new ListDataListener() {
      public void contentsChanged(ListDataEvent e) {
      }

      public void intervalAdded(ListDataEvent e) {
      }

      public void intervalRemoved(ListDataEvent e) {
        if (myList.getModel().getSize() == 0){
          mySelectedSchema = null;
          setNameAndDescription(false, "", "");
          ((DefaultMutableTreeNode)myActionsTree.getModel().getRoot()).removeAllChildren();
          ((DefaultTreeModel)myActionsTree.getModel()).reload();
        }
      }
    });

    myDescription.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        if (mySelectedSchema != null){
          mySelectedSchema.setDescription(myDescription.getText());
        }
      }
    });

    myName.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        if (mySelectedSchema != null){
          mySelectedSchema.setName(myName.getText());
          myList.repaint();
        }
      }
    });

    patchActionsTreeCorrespondingToSchema(root);
  }

  private void setButtonsDisabled() {
    myRemoveActionButton.setEnabled(false);
    myAddActionButton.setEnabled(false);
    myAddSeparatorButton.setEnabled(false);
    myMoveActionDownButton.setEnabled(false);
    myMoveActionUpButton.setEnabled(false);
  }

  private boolean isMoveSupported(JTree tree, int dir){
    DefaultMutableTreeNode parent = null;
    final TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths != null) {
      for (int i = 0; i < selectionPaths.length; i++) {
        TreePath treePath = selectionPaths[i];
        if (treePath.getLastPathComponent() != null){
          final DefaultMutableTreeNode node = ((DefaultMutableTreeNode)treePath.getLastPathComponent());
          if (parent == null){
            parent = (DefaultMutableTreeNode)node.getParent();
          }
          if (parent != node.getParent()){
            return false;
          }
          if (dir > 0){
            if (parent.getIndex(node) == parent.getChildCount() - 1){
              return false;
            }
          } else {
            if (parent.getIndex(node) == 0){
              return false;
            }
          }
        }
      }
      return true;
    }
    return false;
  }

  private String createUniqueName() {
    String str = "Unnamed";
    final ArrayList<String> currentNames = new ArrayList<String>();
    for (int i = 0; i < myCustomizationSchemas.getSize(); i++) {
      currentNames.add(((CustomActionsSchema)myCustomizationSchemas.elementAt(i)).getName());
    }
    if (!currentNames.contains(str)) return str;
    int i = 1;
    while (true) {
      if (!currentNames.contains(str + i)) return str + i;
      i++;
    }
  }

  private void fillSchemaList() {
    myCustomizationSchemas.clear();
    final CustomActionsSchema[] customActionsSchemas = CustomizableActionsSchemas.getInstance().getCustomActionsSchemas();
    for (int i = 0; i < customActionsSchemas.length; i++) {
      final CustomActionsSchema customActionsSchema = customActionsSchemas[i].copyFrom();
      myCustomizationSchemas.addElement(customActionsSchema);
      if (CustomizableActionsSchemas.getInstance().getActiveSchema().getName().equals(customActionsSchema.getName())){
        myList.setSelectedValue(customActionsSchema, true);
      }
    }
    if (mySelectedSchema == null){
      setNameAndDescription(false, "", "");
    }
  }

  private void setNameAndDescription(boolean enabled, String name, String description) {
    myName.setEnabled(enabled);
    myDescription.setEnabled(enabled);
    myName.setText(name);
    myDescription.setText(description);
  }

  public String getDescription() {
    return myDescription.getText();
  }

  public String getDisplayName() {
    return ((CustomActionsSchema)myList.getSelectedValue()).getName();
  }

  public JPanel getPanel() {
    return myPanel;
  }

  private void setCustomizationSchemaForCurrentProjects(){
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; i < openProjects.length; i++) {
      Project project = openProjects[i];
      final IdeFrame frame = WindowManagerEx.getInstanceEx().getFrame(project);
      frame.updateToolbar();
      frame.updateMenuBar();
    }
    final IdeFrame frame = WindowManagerEx.getInstanceEx().getFrame(null);
    if (frame != null){
      frame.updateToolbar();
      frame.updateMenuBar();
    }
    CustomizableActionsSchemas.getInstance().setActiveSchema(mySelectedSchema);
  }

  public void apply(){
    if (mySelectedSchema != null){
      CustomizationUtil.optimizeSchema(myActionsTree, mySelectedSchema);
    }
    final CustomizableActionsSchemas allSchemasComponent = CustomizableActionsSchemas.getInstance();
    allSchemasComponent.clear();
    for (int i = 0; i < myCustomizationSchemas.getSize(); i++){
      final CustomActionsSchema schema = (CustomActionsSchema)myCustomizationSchemas.getElementAt(i);
      if (schema.isModified()){
        schema.resetMainActionGroups();
      }
      allSchemasComponent.addCustomActionsSchema(schema);
    }
    allSchemasComponent.setActiveSchema(mySelectedSchema);
    setCustomizationSchemaForCurrentProjects();
    reset();
  }

  public void reset(){
    fillSchemaList();
    patchActionsTreeCorrespondingToSchema((DefaultMutableTreeNode)myActionsTree.getModel().getRoot());
  }

  public boolean isModified() {
    if (mySelectedSchema == null){
      if (CustomizableActionsSchemas.ACTIVE_SCHEMA != CustomizableActionsSchemas.DEFAULT_SCHEMA){
        return true;
      }
      return false;
    }
    final CustomizableActionsSchemas allSchemas = CustomizableActionsSchemas.getInstance();
    if (!mySelectedSchema.getName().equals(allSchemas.getActiveSchema().getName())){
      return true;
    }

    ArrayList<String> currentNames = new ArrayList<String>();

    for (int i = 0; i < myCustomizationSchemas.size(); i++) {
      final CustomActionsSchema customActionsSchema = (CustomActionsSchema)myCustomizationSchemas.elementAt(i);
      currentNames.add(customActionsSchema.getName());
      if (customActionsSchema != mySelectedSchema && customActionsSchema.isModified()){
        //one of current schemas was deleted
        return true;
      }
    }

    if (allSchemas.getCustomActionsSchemas() != null){
      for (int i = 0; i < allSchemas.getCustomActionsSchemas().length; i++) {
        CustomActionsSchema customActionsSchema = allSchemas.getCustomActionsSchemas()[i];
        if (!currentNames.contains(customActionsSchema.getName())){
          //schema was deleted
          return true;
        }
      }
    }

    final CustomActionsSchema storedSchema = allSchemas.getCustomActionsSchema(mySelectedSchema.getName());
    if (storedSchema == null){
      //selected schema was added
      mySelectedSchema.setModified(true);
      return true;
    }

    CustomizationUtil.optimizeSchema(myActionsTree, mySelectedSchema);

    if (storedSchema.getActions().size() != mySelectedSchema.getActions().size()){
      mySelectedSchema.setModified(true);
      return true;
    }
    for (int i = 0; i < mySelectedSchema.getActions().size(); i++) {
      if (!mySelectedSchema.getActions().get(i).equals(storedSchema.getActions().get(i))){
        mySelectedSchema.setModified(true);
        return true;
      }
    }
    mySelectedSchema.setModified(false);
    return false;
  }

  private void patchActionsTreeCorrespondingToSchema(DefaultMutableTreeNode root){
    root.removeAllChildren();
    if (mySelectedSchema != null){
      root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createMainMenuGroup(true)));
      root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createMainToolbarGroup()));
      root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createEditorPopupGroup()));
      for (Iterator<ActionUrl> iterator = mySelectedSchema.getActions().iterator(); iterator.hasNext();) {
        ActionUrl.changePathInActionsTree(myActionsTree, iterator.next());
      }
    }
    ((DefaultTreeModel)myActionsTree.getModel()).reload();
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
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        if (userObject instanceof Group) {
          Group group = (Group)userObject;
          String name = group.getName();
          setText(name != null ? name : group.getId());
          Icon icon = expanded ? group.getOpenIcon() : group.getIcon();

          if (icon == null) {
            icon = expanded ? getOpenIcon() : getClosedIcon();
          }

          setIcon(icon);
        }
        else if (userObject instanceof String) {
          String actionId = (String)userObject;
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
        else if (userObject instanceof Separator) {
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
          setForeground(UIManager.getColor("Tree.foreground"));
        }
      }
      return this;
    }
  }

  private class FindAvailableActionsDialog extends DialogWrapper {
    private JTree myTree;
    FindAvailableActionsDialog() {
      super(true);
      setTitle("Choose Actions To Add");
      Group rootGroup = ActionsTreeUtil.createMainGroup(null, null, new QuickList[0]);
      DefaultMutableTreeNode root = ActionsTreeUtil.createNode(rootGroup);
      DefaultTreeModel model = new DefaultTreeModel(root);
      myTree = new JTree();
      myTree.setModel(model);
      myTree.setCellRenderer(new MyTreeCellRenderer());
      init();
    }

    protected JComponent createCenterPanel() {
      return ScrollPaneFactory.createScrollPane(myTree);
    }

    public Set<Object> getTreeSelectedActionIds() {
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) return null;

      Set<Object> actions = new HashSet<Object>();
      for (int i = 0; i < paths.length; i++) {
        Object node = paths[i].getLastPathComponent();
        if (node instanceof DefaultMutableTreeNode) {
          DefaultMutableTreeNode defNode = (DefaultMutableTreeNode)node;
          Object userObject = defNode.getUserObject();
          actions.add(userObject);
        }
      }
      return actions;
    }
    protected String getDimensionServiceKey() {
      return "#com.intellij.ide.ui.customization.CustomizableActionsPanel.FindAvailableActionsDialog";
    }
  }
}
