/*
 * User: anna
 * Date: 11-Apr-2008
 */
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

public class TypeMigrationTreeBuilder extends AbstractTreeBuilder{
  public TypeMigrationTreeBuilder(JTree tree, Project project) {
    super(tree, (DefaultTreeModel)tree.getModel(), new TypeMigrationTreeStructure(project), AlphaComparator.INSTANCE, false);
    initRootNode();
  }

  protected boolean isAlwaysShowPlus(final NodeDescriptor nodeDescriptor) {
    return false;
  }

  protected boolean isAutoExpandNode(final NodeDescriptor nodeDescriptor) {
    return false;
  }

  public void setRoot(MigrationRootNode root) {
    ((TypeMigrationTreeStructure)getTreeStructure()).setRoot(root);
  }
}