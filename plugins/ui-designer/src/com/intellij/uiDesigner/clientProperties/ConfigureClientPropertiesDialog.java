// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.clientProperties;

import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.uiDesigner.LoaderFactory;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.*;


public class ConfigureClientPropertiesDialog extends DialogWrapper {
  private JTree myClassTree;
  private JTable myPropertiesTable;
  private Class<?> mySelectedClass;
  private List<ClientPropertiesManager.ClientProperty> mySelectedProperties = Collections.emptyList();
  private final MyTableModel myTableModel = new MyTableModel();
  private final Project myProject;
  private final ClientPropertiesManager myManager;
  private JBSplitter mySplitter;

  public ConfigureClientPropertiesDialog(final Project project) {
    super(project, true);
    myProject = project;
    setTitle(UIDesignerBundle.message("client.properties.title"));
    myManager = ClientPropertiesManager.getInstance(project).clone();
    init();
  }

  public void save() {
    ClientPropertiesManager.getInstance(myProject).saveFrom(myManager);
  }

  private void updateSelectedProperties() {
    mySelectedProperties = myManager.getConfiguredProperties(mySelectedClass);
    myTableModel.fireTableDataChanged();
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    myClassTree = new Tree();
    myClassTree.setRootVisible(false);
    myClassTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath leadSelectionPath = e.getNewLeadSelectionPath();
        if (leadSelectionPath == null) return;
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)leadSelectionPath.getLastPathComponent();
        mySelectedClass = (Class<?>)node.getUserObject();
        updateSelectedProperties();
      }
    });

    myClassTree.setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        if (node.getUserObject() instanceof Class<?> cls) {
          @NlsSafe String name = cls.getName();
          append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    });
    fillClassTree();

    myPropertiesTable = new JBTable();
    myPropertiesTable.setModel(myTableModel);


    mySplitter = new JBSplitter("ConfigureClientPropertiesDialog.splitterProportion", 0.5f);
    mySplitter.setFirstComponent(
      ToolbarDecorator.createDecorator(myClassTree)
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            ClassNameInputDialog dlg = new ClassNameInputDialog(myProject, mySplitter);
            dlg.show();
            if (dlg.getExitCode() == OK_EXIT_CODE) {
              String className = dlg.getClassName();
              if (className.isEmpty()) return;
              final Class<?> aClass;
              try {
                aClass = Class.forName(className, true, LoaderFactory.getInstance(myProject).getProjectClassLoader());
              }
              catch (ClassNotFoundException ex) {
                Messages.showErrorDialog(mySplitter,
                                         UIDesignerBundle.message("client.properties.class.not.found", className),
                                         UIDesignerBundle.message("client.properties.title"));
                return;
              }
              if (!JComponent.class.isAssignableFrom(aClass)) {
                Messages.showErrorDialog(mySplitter,
                                         UIDesignerBundle
                                           .message("client.properties.class.not.component", className),
                                         UIDesignerBundle.message("client.properties.title"));
                return;
              }
              myManager.addClientPropertyClass(className);
              fillClassTree();
            }
          }
        }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          if (mySelectedClass != null) {
            myManager.removeClientPropertyClass(mySelectedClass);
            fillClassTree();
          }
        }
      }).setToolbarPosition(SystemInfo.isMac ? ActionToolbarPosition.BOTTOM : ActionToolbarPosition.RIGHT).createPanel());

    mySplitter.setSecondComponent(
      ToolbarDecorator.createDecorator(myPropertiesTable).disableUpDownActions()
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            AddClientPropertyDialog dlg = new AddClientPropertyDialog(myProject);
            dlg.show();
            if (dlg.getExitCode() == OK_EXIT_CODE) {
              for (ClientPropertiesManager.ClientProperty prop : myManager.getClientProperties(mySelectedClass)) {
                if (prop.getName().equalsIgnoreCase(dlg.getEnteredProperty().getName())) {
                  Messages.showErrorDialog(mySplitter,
                                           UIDesignerBundle.message("client.properties.already.defined", prop.getName()),
                                           UIDesignerBundle.message("client.properties.title"));
                  return;
                }
              }
              myManager.addConfiguredProperty(mySelectedClass, dlg.getEnteredProperty());
              updateSelectedProperties();
            }
          }
        }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          int row = myPropertiesTable.getSelectedRow();
          if (row >= 0 && row < mySelectedProperties.size()) {
            myManager.removeConfiguredProperty(mySelectedClass, mySelectedProperties.get(row).getName());
            updateSelectedProperties();
            if (!mySelectedProperties.isEmpty()) {
              if (row >= mySelectedProperties.size()) {
                row--;
              }
              myPropertiesTable.getSelectionModel().setSelectionInterval(row, row);
            }
          }
        }
      }).createPanel());

    return mySplitter;
  }

  private static int getInheritanceLevel(Class<?> aClass) {
    int level = 0;
    while (aClass.getSuperclass() != null) {
      level++;
      aClass = aClass.getSuperclass();
    }
    return level;
  }

  private void fillClassTree() {
    List<Class<?>> configuredClasses = myManager.getConfiguredClasses(myProject);
    configuredClasses.sort(Comparator.comparingInt(ConfigureClientPropertiesDialog::getInheritanceLevel));

    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultTreeModel treeModel = new DefaultTreeModel(root);
    Map<Class<?>, DefaultMutableTreeNode> classToNodeMap = new HashMap<>();
    for (Class<?> cls : configuredClasses) {
      DefaultMutableTreeNode parentNode = root;
      Class<?> superClass = cls.getSuperclass();
      while (superClass != null) {
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
    if (root.getChildCount() > 0) {
      myClassTree.expandRow(0);
      myClassTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      myClassTree.getSelectionModel().setSelectionPath(new TreePath(new Object[]{root, root.getFirstChild()}));
    }
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "ConfigureClientPropertiesDialog";
  }

  private class MyTableModel extends AbstractTableModel {
    @Override
    public int getRowCount() {
      return mySelectedProperties.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (columnIndex == 0) {
        return mySelectedProperties.get(rowIndex).getName();
      }
      return mySelectedProperties.get(rowIndex).getValueClass();
    }

    @Override
    public String getColumnName(int column) {
      if (column == 0) {
        return UIDesignerBundle.message("client.properties.name");
      }
      return UIDesignerBundle.message("client.properties.class");
    }
  }
}
