/*
 * User: anna
 * Date: 11-Apr-2008
 */
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.openapi.project.Project;

import java.util.Collections;
import java.util.List;

public class TypeMigrationTreeStructure extends AbstractTreeStructureBase {
  private MigrationRootNode myRoot;

  public TypeMigrationTreeStructure(final Project project) {
    super(project);
  }

  public void setRoot(final MigrationRootNode root) {
    myRoot = root;
  }

  public List<TreeStructureProvider> getProviders() {
    return Collections.emptyList();
  }

  public Object getRootElement() {
    return myRoot;
  }

  public void commit() {

  }

  public boolean hasSomethingToCommit() {
    return false;
  }

  @Override
  public boolean isToBuildChildrenInBackground(final Object element) {
    return true;
  }
}