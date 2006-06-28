/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.clientProperties;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.util.*;

/**
 * @author yole
 */
public class ConfigureClientPropertiesDialog extends DialogWrapper {
  private JPanel myRootPanel;
  private JTree myClassTree;
  private JTable myPropertiesTable;
  private JSplitPane mySplitPane;
  private JPanel myClassToolBarPanel;
  private JPanel myPropertyToolBarPanel;
  private Class mySelectedClass;
  private ClientPropertiesManager.ClientProperty[] mySelectedProperties = new ClientPropertiesManager.ClientProperty[0];
  private MyTableModel myTableModel = new MyTableModel();
  private final Project myProject;
  private ClientPropertiesManager myManager;

  public ConfigureClientPropertiesDialog(final Project project) {
    super(project, true);
    myProject = project;
    init();
    setTitle(UIDesignerBundle.message("client.properties.title"));
    myManager = ClientPropertiesManager.getInstance(project).clone();

    myClassTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath leadSelectionPath = e.getNewLeadSelectionPath();
        if (leadSelectionPath == null) return;
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode) leadSelectionPath.getLastPathComponent();
        mySelectedClass = (Class) node.getUserObject();
        updateSelectedProperties();
      }
    });

    myClassTree.setCellRenderer(new ColoredTreeCellRenderer() {
      public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        if (node.getUserObject() instanceof Class) {
          Class cls = (Class) node.getUserObject();
          if (cls != null) {
            append(cls.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        }
      }
    });

    createToolBar(new AddClassAction(), new RemoveClassAction(), myClassToolBarPanel, myClassTree);
    createToolBar(new AddPropertyAction(), new RemovePropertyAction(), myPropertyToolBarPanel, myPropertiesTable);

    myPropertiesTable.setModel(myTableModel);

    final int location = DimensionService.getInstance().getExtendedState(getDimensionKey());
    if (location > 0) {
      mySplitPane.setDividerLocation(location);
    }

    fillClassTree();
  }

  private static void createToolBar(final AnAction addAction,
                                    final AnAction removeAction,
                                    final JPanel panel,
                                    final JComponent shortcutHost) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(addAction);
    group.add(removeAction);
    ActionToolbar toolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    panel.add(toolBar.getComponent(), BorderLayout.CENTER);
    addAction.registerCustomShortcutSet(CommonShortcuts.INSERT, shortcutHost);
    removeAction.registerCustomShortcutSet(CommonShortcuts.DELETE, shortcutHost);
  }

  public void save() {
    ClientPropertiesManager.getInstance(myProject).saveFrom(myManager);
  }

  @Override
  public void dispose() {
    DimensionService.getInstance().setExtendedState(getDimensionKey(), mySplitPane.getDividerLocation());
    super.dispose();
  }

  private void fillClassTree() {
    List<Class> configuredClasses = myManager.getConfiguredClasses();
    Collections.sort(configuredClasses, new Comparator<Class>() {
      public int compare(final Class o1, final Class o2) {
        return getInheritanceLevel(o1) - getInheritanceLevel(o2);
      }

      private int getInheritanceLevel(Class aClass) {
        int level = 0;
        while(aClass.getSuperclass() != null) {
          level++;
          aClass = aClass.getSuperclass();
        }
        return level;
      }
    });

    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultTreeModel treeModel = new DefaultTreeModel(root);
    Map<Class, DefaultMutableTreeNode> classToNodeMap = new HashMap<Class, DefaultMutableTreeNode>();
    for(Class cls: configuredClasses) {
      DefaultMutableTreeNode parentNode = root;
      Class superClass = cls.getSuperclass();
      while(superClass != null) {
        if (classToNodeMap.containsKey(superClass)) {
          parentNode = classToNodeMap.get(superClass);
          break;
        }
        superClass = superClass.getSuperclass();
      }
      DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(cls);
      classToNodeMap.put(cls, childNode);
      parentNode.add(childNode);
    }
    myClassTree.setModel(treeModel);
    myClassTree.expandRow(0);
    myClassTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myClassTree.getSelectionModel().setSelectionPath(new TreePath(new Object[] { root, root.getFirstChild() }));
  }

  private void updateSelectedProperties() {
    mySelectedProperties = ClientPropertiesManager.getInstance(myProject).getConfiguredProperties(mySelectedClass);
    myTableModel.fireTableDataChanged();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @Override @NonNls
  protected String getDimensionServiceKey() {
    return "ConfigureClientPropertiesDialog";
  }

  private class MyTableModel extends AbstractTableModel {
    public int getRowCount() {
      return mySelectedProperties.length;
    }

    public int getColumnCount() {
      return 2;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch(columnIndex) {
        case 0: return mySelectedProperties [rowIndex].getName();
        default: return mySelectedProperties [rowIndex].getValueClass();
      }
    }

    @Override
    public String getColumnName(int column) {
      switch(column) {
        case 0: return UIDesignerBundle.message("client.properties.name");
        default: return UIDesignerBundle.message("client.properties.class");
      }
    }
  }

  private class AddClassAction extends AnAction {
    public AddClassAction() {
      super(UIDesignerBundle.message("client.properties.add.class.tooltip"), "", IconLoader.getIcon("/general/add.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      ClassNameInputDialog dlg = new ClassNameInputDialog(myProject, myRootPanel);
      dlg.show();
      if (dlg.getExitCode() == OK_EXIT_CODE) {
        String className = dlg.getClassName();
        if (className.length() == 0) return;
        final Class aClass;
        try {
          aClass = Class.forName(className);
        }
        catch(ClassNotFoundException ex) {
          Messages.showErrorDialog(myRootPanel,
                                   UIDesignerBundle.message("client.properties.class.not.found", className),
                                   UIDesignerBundle.message("client.properties.title"));
          return;
        }
        if (!JComponent.class.isAssignableFrom(aClass)) {
          Messages.showErrorDialog(myRootPanel,
                                   UIDesignerBundle.message("client.properties.class.not.component", className),
                                   UIDesignerBundle.message("client.properties.title"));
          return;
        }
        myManager.addClientPropertyClass(className);
        fillClassTree();
      }
    }
  }

  private class RemoveClassAction extends AnAction {
    public RemoveClassAction() {
      super(UIDesignerBundle.message("client.properties.remove.class.tooltip"), "", IconLoader.getIcon("/general/remove.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      if (mySelectedClass != null) {
        myManager.removeClientPropertyClass(mySelectedClass);
      }
    }
  }

  private class AddPropertyAction extends AnAction {
    public AddPropertyAction() {
      super(UIDesignerBundle.message("client.properties.add.property.tooltip"), "", IconLoader.getIcon("/general/add.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      AddClientPropertyDialog dlg = new AddClientPropertyDialog(myProject);
      dlg.show();
      if (dlg.getExitCode() == OK_EXIT_CODE) {
        ClientPropertiesManager.ClientProperty[] props = myManager.getClientProperties(mySelectedClass);
        for(ClientPropertiesManager.ClientProperty prop: props) {
          if (prop.getName().equalsIgnoreCase(dlg.getEnteredProperty().getName())) {
            Messages.showErrorDialog(myRootPanel,
                                     UIDesignerBundle.message("client.properties.already.defined", prop.getName()),
                                     UIDesignerBundle.message("client.properties.title"));
            return;
          }
        }
        myManager.addConfiguredProperty(mySelectedClass, dlg.getEnteredProperty());
        updateSelectedProperties();
      }
    }
  }

  private class RemovePropertyAction extends AnAction {
    public RemovePropertyAction() {
      super(UIDesignerBundle.message("client.properties.remove.property.tooltip"), "", IconLoader.getIcon("/general/remove.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      int row = myPropertiesTable.getSelectedRow();
      if (row >= 0 && row < mySelectedProperties.length) {
        myManager.removeConfiguredProperty(mySelectedClass, mySelectedProperties [row].getName());
        updateSelectedProperties();
      }
    }
  }
}
