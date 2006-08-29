package com.intellij.ide.ui.customization;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrame;
import com.intellij.ui.*;
import com.intellij.util.ImageLoader;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: Mar 17, 2005
 */
public class CustomizableActionsPanel {
  private static final Icon EMPTY_ICON = new EmptyIcon(18, 18);

  private static final Icon QUICK_LIST_ICON = IconLoader.getIcon("/actions/quickList.png");

  private JButton myEditIconButton;
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
  public static final Icon FULLISH_ICON = IconLoader.getIcon("/toolbar/unknown.png");

  public CustomizableActionsPanel() {
    myList.setModel(myCustomizationSchemas);
    //noinspection HardCodedStringLiteral
    myList.setPrototypeCellValue(new CustomActionsSchema("xxxxxxxxxxxxx", ""));
    fillSchemaList();
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new ColoredListCellRenderer() {
      protected void customizeCellRenderer(
        JList list,
        Object value,
        int index,
        boolean selected,
        boolean hasFocus
      ) {
        if (value instanceof CustomActionsSchema) {
          final CustomActionsSchema schema = (CustomActionsSchema)value;
          append(schema.getName(),
                 schema.getName().equals(CustomizableActionsSchemas.DEFAULT_NAME)
                 ? SimpleTextAttributes.GRAYED_ATTRIBUTES
                 : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    });
    //noinspection HardCodedStringLiteral
    Group rootGroup = new Group("root", null, null);
    DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootGroup);
    DefaultTreeModel model = new DefaultTreeModel(root);
    myActionsTree.setModel(model);

    myActionsTree.setRootVisible(false);
    myActionsTree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(myActionsTree);
    myActionsTree.setCellRenderer(new MyTreeCellRenderer());

    setButtonsDisabled();
    final ActionManager actionManager = ActionManager.getInstance();
    myActionsTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath[] selectionPaths = myActionsTree.getSelectionPaths();
        final boolean isSingleSelection = selectionPaths != null && selectionPaths.length == 1;
        myAddActionButton.setEnabled(isSingleSelection);
        if (isSingleSelection) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPaths[0].getLastPathComponent();
          String actionId = getActionId(node);
          if (actionId != null) {
            final AnAction action = actionManager.getAction(actionId);
            myEditIconButton.setEnabled(action != null &&
                                        (!action.isDefaultIcon() ||
                                         (action.getTemplatePresentation() != null && action.getTemplatePresentation().getIcon() == null)));
          }
          else {
            myEditIconButton.setEnabled(false);
          }
        }
        else {
          myEditIconButton.setEnabled(false);
        }
        myAddSeparatorButton.setEnabled(isSingleSelection);
        myRemoveActionButton.setEnabled(selectionPaths != null);
        if (selectionPaths != null) {
          for (TreePath selectionPath : selectionPaths) {
            if ((selectionPath.getPath() != null && selectionPath.getPath().length <= 2) ||
                (mySelectedSchema == null || mySelectedSchema.getName().equals(CustomizableActionsSchemas.DEFAULT_NAME))) {
              setButtonsDisabled();
              return;
            }
          }
        }
        myMoveActionUpButton.setEnabled(isMoveSupported(myActionsTree, -1));
        myMoveActionDownButton.setEnabled(isMoveSupported(myActionsTree, 1));
      }
    });
    final CustomizableActionsSchemas schemas = CustomizableActionsSchemas.getInstance();
    myAddActionButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath selectionPath = myActionsTree.getLeadSelectionPath();
        if (selectionPath != null) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          final FindAvailableActionsDialog dlg = new FindAvailableActionsDialog();
          dlg.show();
          if (dlg.isOK()) {
            final Set<Object> toAdd = dlg.getTreeSelectedActionIds();
            if (toAdd == null) return;
            for (final Object o : toAdd) {
              final ActionUrl url = new ActionUrl(ActionUrl.getGroupPath(new TreePath(node.getPath())), o, ActionUrl.ADDED,
                                                  node.getParent().getIndex(node) + 1);
              mySelectedSchema.addAction(url);
              ActionUrl.changePathInActionsTree(myActionsTree, url);
              if (o instanceof String) {
                DefaultMutableTreeNode current = new DefaultMutableTreeNode(url.getComponent());
                current.setParent((DefaultMutableTreeNode)node.getParent());
                editToolbarIcon((String)o, current, schemas);
              }
            }
            ((DefaultTreeModel)myActionsTree.getModel()).reload();
          }
        }
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      }
    });

    myEditIconButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath selectionPath = myActionsTree.getLeadSelectionPath();
        if (selectionPath != null) {
          EditIconDialog dlg = new EditIconDialog((DefaultMutableTreeNode)selectionPath.getLastPathComponent());
          dlg.show();
          if (dlg.isOK()) {
            myActionsTree.repaint();
          }
        }
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      }
    });

    myAddSeparatorButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath selectionPath = myActionsTree.getLeadSelectionPath();
        if (selectionPath != null) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          final ActionUrl url = new ActionUrl(ActionUrl.getGroupPath(selectionPath), Separator.getInstance(), ActionUrl.ADDED,
                                              node.getParent().getIndex(node) + 1);
          ActionUrl.changePathInActionsTree(myActionsTree, url);
          mySelectedSchema.addAction(url);
          ((DefaultTreeModel)myActionsTree.getModel()).reload();
        }
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      }
    });


    myRemoveActionButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath[] selectionPath = myActionsTree.getSelectionPaths();
        if (selectionPath != null) {
          for (TreePath treePath : selectionPath) {
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
        final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath[] selectionPath = myActionsTree.getSelectionPaths();
        if (selectionPath != null) {
          for (TreePath treePath : selectionPath) {
            final ActionUrl url = CustomizationUtil.getActionUrl(treePath, ActionUrl.MOVE);
            final int absolutePosition = url.getAbsolutePosition();
            url.setInitialPosition(absolutePosition);
            url.setAbsolutePosition(absolutePosition - 1);
            ActionUrl.changePathInActionsTree(myActionsTree, url);
            mySelectedSchema.addAction(url);
          }
          ((DefaultTreeModel)myActionsTree.getModel()).reload();
          TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
          for (TreePath path : selectionPath) {
            myActionsTree.addSelectionPath(path);
          }
        }
      }
    });

    myMoveActionDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
        final TreePath[] selectionPath = myActionsTree.getSelectionPaths();
        if (selectionPath != null) {
          for (int i = selectionPath.length - 1; i >= 0; i--) {
            TreePath treePath = selectionPath[i];
            final ActionUrl url = CustomizationUtil.getActionUrl(treePath, ActionUrl.MOVE);
            final int absolutePosition = url.getAbsolutePosition();
            url.setInitialPosition(absolutePosition);
            url.setAbsolutePosition(absolutePosition + 1);
            ActionUrl.changePathInActionsTree(myActionsTree, url);
            mySelectedSchema.addAction(url);
          }
          ((DefaultTreeModel)myActionsTree.getModel()).reload();
          TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
          for (TreePath path : selectionPath) {
            myActionsTree.addSelectionPath(path);
          }
        }
      }
    });

    myDescription.setText(mySelectedSchema != null ? mySelectedSchema.getDescription() : "");

    final DefaultActionGroup group = new DefaultActionGroup();
    ReorderableListController<CustomActionsSchema> controller = ReorderableListController.create(myList, group);
    controller.addAddAction(IdeBundle.message("action.add.customization.schema"), new Factory<CustomActionsSchema>() {
      public CustomActionsSchema create() {
        return new CustomActionsSchema(createUniqueName(), "");
      }
    }, true);

    final ReorderableListController<CustomActionsSchema>.RemoveActionDescription removeActionDescription = controller
      .addRemoveAction(IdeBundle.message("action.remove.customization.schema"));
    removeActionDescription.setEnableCondition(new Condition<CustomActionsSchema>() {
      public boolean value(final CustomActionsSchema schema) {
        return !schema.getName().equals(IdeBundle.message("customizations.schema.default"));
      }
    });
    controller
      .addCopyAction(IdeBundle.message("action.copy.customization.schema"), new Convertor<CustomActionsSchema, CustomActionsSchema>() {
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
        if (selectedValue != null) {
          mySelectedSchema = selectedValue;
          setNameAndDescription(!mySelectedSchema.getName().equals(CustomizableActionsSchemas.DEFAULT_NAME),
                                mySelectedSchema.getName(), mySelectedSchema.getDescription());
          patchActionsTreeCorrespondingToSchema((DefaultMutableTreeNode)myActionsTree.getModel().getRoot());
        }
        else {
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
        if (myList.getModel().getSize() == 0) {
          mySelectedSchema = null;
          setNameAndDescription(false, "", "");
          ((DefaultMutableTreeNode)myActionsTree.getModel().getRoot()).removeAllChildren();
          ((DefaultTreeModel)myActionsTree.getModel()).reload();
        }
      }
    });

    myDescription.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        if (mySelectedSchema != null) {
          mySelectedSchema.setDescription(myDescription.getText());
        }
      }
    });

    myName.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        if (mySelectedSchema != null) {
          mySelectedSchema.setName(myName.getText());
          myList.repaint();
        }
      }
    });


    patchActionsTreeCorrespondingToSchema(root);
  }

  private void editToolbarIcon(String actionId, DefaultMutableTreeNode node, CustomizableActionsSchemas schemas) {
    final AnAction anAction = ActionManager.getInstance().getAction(actionId);
    if (isToolbarAction(node) &&
        anAction.getTemplatePresentation() != null &&
        anAction.getTemplatePresentation().getIcon() == null) {
      final int exitCode = Messages.showOkCancelDialog(IdeBundle.message("error.adding.action.without.icon.to.toolbar"),
                                                       IdeBundle.message("title.unable.to.add.action.without.icon.to.toolbar"),
                                                       Messages.getInformationIcon());
      if (exitCode == DialogWrapper.OK_EXIT_CODE) {
        schemas.addIconCustomization(actionId, null);
        anAction.getTemplatePresentation().setIcon(FULLISH_ICON);
        anAction.setDefaultIcon(false);
        node.setUserObject(Pair.create(actionId, FULLISH_ICON));
        myActionsTree.repaint();
        setCustomizationSchemaForCurrentProjects();
      }
    }
  }

  private void setButtonsDisabled() {
    myRemoveActionButton.setEnabled(false);
    myAddActionButton.setEnabled(false);
    myEditIconButton.setEnabled(false);
    myAddSeparatorButton.setEnabled(false);
    myMoveActionDownButton.setEnabled(false);
    myMoveActionUpButton.setEnabled(false);
  }

  private static boolean isMoveSupported(JTree tree, int dir) {
    final TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths != null) {
      DefaultMutableTreeNode parent = null;
      for (TreePath treePath : selectionPaths)
        if (treePath.getLastPathComponent() != null) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
          if (parent == null) {
            parent = (DefaultMutableTreeNode)node.getParent();
          }
          if (parent != node.getParent()) {
            return false;
          }
          if (dir > 0) {
            if (parent.getIndex(node) == parent.getChildCount() - 1) {
              return false;
            }
          }
          else {
            if (parent.getIndex(node) == 0) {
              return false;
            }
          }
        }
      return true;
    }
    return false;
  }

  private String createUniqueName() {
    String str = IdeBundle.message("template.unnamed");
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
    final CustomizableActionsSchemas schemas = CustomizableActionsSchemas.getInstance();
    final CustomActionsSchema[] customActionsSchemas = schemas.getCustomActionsSchemas();
    Arrays.sort(customActionsSchemas, new Comparator<CustomActionsSchema>() {
      public int compare(final CustomActionsSchema o1, final CustomActionsSchema o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    for (CustomActionsSchema customActionsSchema1 : customActionsSchemas) {
      final CustomActionsSchema customActionsSchema = customActionsSchema1.copyFrom();
      myCustomizationSchemas.addElement(customActionsSchema);
      if (schemas.getActiveSchema().getName().equals(customActionsSchema.getName())) {
        myList.setSelectedValue(customActionsSchema, true);
      }
    }
    if (mySelectedSchema == null) {
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

  private static void setCustomizationSchemaForCurrentProjects() {
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : openProjects) {
      final IdeFrame frame = WindowManagerEx.getInstanceEx().getFrame(project);
      frame.updateToolbar();
      frame.updateMenuBar();

      //final FavoritesManager favoritesView = FavoritesManager.getInstance(project);
      //final String[] availableFavoritesLists = favoritesView.getAvailableFavoritesLists();
      //for (String favoritesList : availableFavoritesLists) {
      //  favoritesView.getFavoritesTreeViewPanel(favoritesList).updateTreePopupHandler();
      //}
    }
    final IdeFrame frame = WindowManagerEx.getInstanceEx().getFrame(null);
    if (frame != null) {
      frame.updateToolbar();
      frame.updateMenuBar();
    }
  }

  public void apply() throws ConfigurationException {
    final List<TreePath> treePaths = TreeUtil.collectExpandedPaths(myActionsTree);
    if (mySelectedSchema != null) {
      CustomizationUtil.optimizeSchema(myActionsTree, mySelectedSchema);
    }
    final CustomizableActionsSchemas allSchemasComponent = CustomizableActionsSchemas.getInstance();
    allSchemasComponent.clear();
    Set<String> names = new HashSet<String>();
    for (int i = 0; i < myCustomizationSchemas.getSize(); i++) {
      final CustomActionsSchema schema = (CustomActionsSchema)myCustomizationSchemas.getElementAt(i);
      final String name = schema.getName();
      if (names.contains(name)) {
        throw new ConfigurationException(IdeBundle.message("error.please.specify.new.name.for.schema", name));
      }
      names.add(name);
      if (schema.isModified()) {
        schema.resetMainActionGroups();
      }
      allSchemasComponent.addCustomActionsSchema(schema);
    }
    allSchemasComponent.setActiveSchema(mySelectedSchema);
    setCustomizationSchemaForCurrentProjects();
    fillSchemaList();
    restorePathsAfterTreeOptimization(treePaths);
  }

  private void restorePathsAfterTreeOptimization(final List<TreePath> treePaths) {
    for (final TreePath treePath : treePaths) {
      myActionsTree.expandPath(CustomizationUtil.getPathByUserObjects(myActionsTree, treePath));
    }
  }

  public void reset() {
    fillSchemaList();
    patchActionsTreeCorrespondingToSchema((DefaultMutableTreeNode)myActionsTree.getModel().getRoot());
  }

  public boolean isModified() {
    if (mySelectedSchema == null) {
      return CustomizableActionsSchemas.ACTIVE_SCHEMA != CustomizableActionsSchemas.DEFAULT_SCHEMA;
    }
    final CustomizableActionsSchemas allSchemas = CustomizableActionsSchemas.getInstance();
    if (!mySelectedSchema.getName().equals(allSchemas.getActiveSchema().getName())) {
      return true;
    }

    if (Comparing.strEqual(mySelectedSchema.getName(), CustomizableActionsSchemas.DEFAULT_NAME)) return false;

    ArrayList<String> currentNames = new ArrayList<String>();

    for (int i = 0; i < myCustomizationSchemas.size(); i++) {
      final CustomActionsSchema customActionsSchema = (CustomActionsSchema)myCustomizationSchemas.elementAt(i);
      currentNames.add(customActionsSchema.getName());
      if (customActionsSchema != mySelectedSchema && customActionsSchema.isModified()) {
        //one of current schemas was deleted
        return true;
      }
    }

    if (allSchemas.getCustomActionsSchemas() != null) {
      for (int i = 0; i < allSchemas.getCustomActionsSchemas().length; i++) {
        CustomActionsSchema customActionsSchema = allSchemas.getCustomActionsSchemas()[i];
        if (!currentNames.contains(customActionsSchema.getName())) {
          //schema was deleted
          return true;
        }
      }
    }

    final CustomActionsSchema storedSchema = allSchemas.getCustomActionsSchema(mySelectedSchema.getName());
    if (storedSchema == null) {
      //selected schema was added
      mySelectedSchema.setModified(true);
      return true;
    }

    CustomizationUtil.optimizeSchema(myActionsTree, mySelectedSchema);

    if (storedSchema.getActions().size() != mySelectedSchema.getActions().size()) {
      mySelectedSchema.setModified(true);
      return true;
    }
    for (int i = 0; i < mySelectedSchema.getActions().size(); i++) {
      if (!mySelectedSchema.getActions().get(i).equals(storedSchema.getActions().get(i))) {
        mySelectedSchema.setModified(true);
        return true;
      }
    }
    mySelectedSchema.setModified(false);
    return false;
  }

  private void patchActionsTreeCorrespondingToSchema(DefaultMutableTreeNode root) {
    root.removeAllChildren();
    if (mySelectedSchema != null) {
      mySelectedSchema.fillActionGroups(root);
      for (final ActionUrl actionUrl : mySelectedSchema.getActions()) {
        ActionUrl.changePathInActionsTree(myActionsTree, actionUrl);
      }
    }
    ((DefaultTreeModel)myActionsTree.getModel()).reload();
  }

  private static class MyTreeCellRenderer extends DefaultTreeCellRenderer {
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
        Icon icon = null;
        if (userObject instanceof Group) {
          Group group = (Group)userObject;
          String name = group.getName();
          setText(name != null ? name : group.getId());
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

        }
        else if (userObject instanceof Pair) {
          String actionId = (String)((Pair)userObject).first;
          AnAction action = ActionManager.getInstance().getAction(actionId);
          setText(action != null ? action.getTemplatePresentation().getText() : actionId);
          icon = (Icon)((Pair)userObject).second;
        }
        else if (userObject instanceof Separator) {
          setText("-------------");
        }
        else if (userObject instanceof QuickList) {
          setText(((QuickList)userObject).getDisplayName());
          icon = QUICK_LIST_ICON;
        }
        else {
          throw new IllegalArgumentException("unknown userObject: " + userObject);
        }

        LayeredIcon layeredIcon = new LayeredIcon(2);
        layeredIcon.setIcon(EMPTY_ICON, 0);
        if (icon != null) {
          layeredIcon.setIcon(icon, 1, (- icon.getIconWidth() + EMPTY_ICON.getIconWidth()) / 2,
                              (EMPTY_ICON.getIconHeight() - icon.getIconHeight()) / 2);
        }
        setIcon(layeredIcon);

        if (sel) {
          setForeground(UIUtil.getTreeSelectionForeground());
        }
        else {
          setForeground(UIUtil.getTreeForeground());
        }
      }
      return this;
    }
  }

  private static boolean isToolbarAction(DefaultMutableTreeNode node) {
    return node.getParent() != null && ((DefaultMutableTreeNode)node.getParent()).getUserObject() instanceof Group &&
           ((Group)((DefaultMutableTreeNode)node.getParent()).getUserObject()).getName().equals(ActionsTreeUtil.MAIN_TOOLBAR);
  }

  @Nullable
  private static String getActionId(DefaultMutableTreeNode node) {
    return (String)(node.getUserObject() instanceof String ? node.getUserObject() :
                    node.getUserObject() instanceof Pair ? ((Pair)node.getUserObject()).first : null);
  }

  protected void doSetIcon(DefaultMutableTreeNode node, String path) {
    String actionId = getActionId(node);
    if (actionId == null) return;
    final CustomizableActionsSchemas schemas = CustomizableActionsSchemas.getInstance();
    final AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action != null && action.getTemplatePresentation() != null) {
      if (path != null && path.length() > 0) {
        final Image image = ImageLoader.loadFromURL(VfsUtil.convertToURL(VfsUtil.pathToUrl(path.replace(File.separatorChar,
                                                                                                        '/'))));
        Icon icon = new File(path).exists() ? IconLoader.getIcon(image) : null;
        if (icon != null) {
          node.setUserObject(Pair.create(actionId, icon));
          schemas.addIconCustomization(actionId, path);
        }
      }
      else {
        node.setUserObject(Pair.create(actionId, null));
        schemas.removeIconCustomization(actionId);
        final DefaultMutableTreeNode nodeOnToolbar = findNodeOnToolbar(actionId);
        if (nodeOnToolbar != null){
          editToolbarIcon(actionId, nodeOnToolbar, schemas);
          node.setUserObject(nodeOnToolbar.getUserObject());
        }
      }
    }
  }

  private static TextFieldWithBrowseButton createBrowseField(){
    TextFieldWithBrowseButton textField = new TextFieldWithBrowseButton();
    textField.setPreferredSize(new Dimension(150, textField.getPreferredSize().height));
    textField.setMinimumSize(new Dimension(150, textField.getPreferredSize().height));
    final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      public boolean isFileSelectable(VirtualFile file) {
        //noinspection HardCodedStringLiteral
        return file.getName().endsWith(".png");
      }
    };
    textField.addBrowseFolderListener(IdeBundle.message("title.browse.icon"), IdeBundle.message("prompt.browse.icon.for.selected.action"), null,
                                      fileChooserDescriptor);
    InsertPathAction.addTo(textField.getTextField(), fileChooserDescriptor);
    return textField;
  }

  private class EditIconDialog extends DialogWrapper {
    private DefaultMutableTreeNode myNode;
    protected TextFieldWithBrowseButton myTextField;

    protected EditIconDialog(DefaultMutableTreeNode node) {
      super(false);
      setTitle(IdeBundle.message("title.choose.action.icon.path"));
      init();
      myNode = node;
      final String actionId = getActionId(node);
      if (actionId != null) {
        myTextField.setText(CustomizableActionsSchemas.getInstance().getIconPath(actionId));
      }
    }

    protected JComponent createCenterPanel() {
      myTextField = createBrowseField();
      JPanel northPanel = new JPanel(new BorderLayout());
      northPanel.add(myTextField, BorderLayout.NORTH);
      return northPanel;
    }

    protected void doOKAction() {
      if (myNode != null) {
        if (myTextField.getText().length() > 0 && !new File(myTextField.getText()).exists()){
          Messages.showErrorDialog(myPanel, IdeBundle.message("error.file.not.found.message", myTextField.getText()));
          return;
        }
        doSetIcon(myNode, myTextField.getText());
        final Object userObject = myNode.getUserObject();
        if (userObject instanceof Pair) {
          String actionId = (String)((Pair)userObject).first;
          final AnAction action = ActionManager.getInstance().getAction(actionId);
          final Icon icon = (Icon)((Pair)userObject).second;
          action.getTemplatePresentation().setIcon(icon);
          action.setDefaultIcon(icon == null);
          editToolbarIcon(actionId, myNode, CustomizableActionsSchemas.getInstance());
        }
        myActionsTree.repaint();
      }
      setCustomizationSchemaForCurrentProjects();
      super.doOKAction();
    }
  }

  @Nullable
  private DefaultMutableTreeNode findNodeOnToolbar(String actionId){
    final TreeNode toolbar = ((DefaultMutableTreeNode)myActionsTree.getModel().getRoot()).getChildAt(1);
    for(int i = 0; i < toolbar.getChildCount(); i++){
      final DefaultMutableTreeNode child = (DefaultMutableTreeNode)toolbar.getChildAt(i);
      final String childId = getActionId(child);
      if (childId != null && childId.equals(actionId)){
        return child;
      }
    }
    return null;
  }

  private class FindAvailableActionsDialog extends DialogWrapper{
    private JTree myTree;
    private JButton mySetIconButton;
    private TextFieldWithBrowseButton myTextField;

    FindAvailableActionsDialog() {
      super(false);
      setTitle(IdeBundle.message("action.choose.actions.to.add"));
      init();
    }

    protected JComponent createCenterPanel() {
      final CustomizableActionsSchemas schemas = CustomizableActionsSchemas.getInstance();
      Group rootGroup = ActionsTreeUtil.createMainGroup(null, null, QuickListsManager.getInstance().getAllQuickLists());
      DefaultMutableTreeNode root = ActionsTreeUtil.createNode(rootGroup);
      DefaultTreeModel model = new DefaultTreeModel(root);
      myTree = new JTree();
      myTree.setModel(model);
      myTree.setCellRenderer(new MyTreeCellRenderer());
      final ActionManager actionManager = ActionManager.getInstance();

      mySetIconButton = new JButton(IdeBundle.message("button.set.icon"));
      mySetIconButton.setEnabled(false);
      mySetIconButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          final TreePath selectionPath = myTree.getSelectionPath();
          if (selectionPath != null) {
            doSetIcon((DefaultMutableTreeNode)selectionPath.getLastPathComponent(), myTextField.getText());
            myTree.repaint();
          }
        }
      });
      myTextField = createBrowseField();
      myTextField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
        protected void textChanged(DocumentEvent e) {
          enableSetIconButton(actionManager);
        }
      });
      JPanel northPanel = new JPanel(new BorderLayout());
      northPanel.add(myTextField, BorderLayout.CENTER);
      northPanel.add(new JLabel(IdeBundle.message("label.icon.path")), BorderLayout.WEST);
      northPanel.add(mySetIconButton, BorderLayout.EAST);
      northPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(northPanel, BorderLayout.NORTH);

      panel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
      myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) {
          enableSetIconButton(actionManager);
          final TreePath selectionPath = myTree.getSelectionPath();
          if (selectionPath != null) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
            final String actionId = getActionId(node);
            if (actionId != null) {
              myTextField.setText(schemas.getIconPath(actionId));
            }
          }
        }
      });
      return panel;
    }

    protected void doOKAction() {
      final ActionManager actionManager = ActionManager.getInstance();
      final CustomizableActionsSchemas schemas = CustomizableActionsSchemas.getInstance();
      TreeUtil.traverseDepth((TreeNode)myTree.getModel().getRoot(), new TreeUtil.Traverse() {
        public boolean accept(Object node) {
          if (node instanceof DefaultMutableTreeNode) {
            final DefaultMutableTreeNode mutableNode = (DefaultMutableTreeNode)node;
            final Object userObject = mutableNode.getUserObject();
            if (userObject instanceof Pair) {
              String actionId = (String)((Pair)userObject).first;
              final AnAction action = actionManager.getAction(actionId);
              Icon icon = (Icon)((Pair)userObject).second;
              action.getTemplatePresentation().setIcon(icon);
              action.setDefaultIcon(icon == null);
              editToolbarIcon(actionId, mutableNode, schemas);
            }
          }
          return true;
        }
      });
      super.doOKAction();
      setCustomizationSchemaForCurrentProjects();
    }

    protected void enableSetIconButton(ActionManager actionManager) {
      final TreePath selectionPath = myTree.getSelectionPath();
      Object userObject = null;
      if (selectionPath != null) {
        userObject = ((DefaultMutableTreeNode)selectionPath.getLastPathComponent()).getUserObject();
        if (userObject instanceof String) {
          final AnAction action = actionManager.getAction((String)userObject);
          if (action != null &&
              action.getTemplatePresentation() != null &&
              action.getTemplatePresentation().getIcon() != null) {
            mySetIconButton.setEnabled(!action.isDefaultIcon());
            return;
          }
        }
      }
      mySetIconButton.setEnabled(myTextField.getText().length() != 0 &&
                                 selectionPath != null &&
                                 new DefaultMutableTreeNode(selectionPath).isLeaf() &&
                                 !(userObject instanceof Separator));
    }

    @Nullable
    public Set<Object> getTreeSelectedActionIds() {
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) return null;

      Set<Object> actions = new HashSet<Object>();
      for (TreePath path : paths) {
        Object node = path.getLastPathComponent();
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
