package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PackageViewProjectNode;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;

/**
 * @author ven
 * */

class ProjectTreeStructure extends AbstractProjectTreeStructure {

  protected String myId;

  public ProjectTreeStructure(Project project, final String ID) {
    super(project, new RootCreator() {
      public AbstractTreeNode createRoot(Project project, ViewSettings settings) {
        return ProjectTreeStructure.createRoot(project, ID, settings);
      }
    });
    myId = ID;
  }

  private static AbstractTreeNode createRoot(final Project project, final String id, ViewSettings settings) {
    if (PackageViewPane.ID.equals(id)) return new PackageViewProjectNode(project, settings);
    else return new ProjectViewProjectNode(project, settings);
  }

  public boolean isFlattenPackages() {
    return ProjectView.getInstance(myProject).isFlattenPackages(myId);
  }

  public boolean isShowMembers() {
    return ProjectView.getInstance(myProject).isShowMembers(myId);
  }

  public boolean isHideEmptyMiddlePackages() {
    return ProjectView.getInstance(myProject).isHideEmptyMiddlePackages(myId);
  }

  public boolean isAbbreviatePackageNames() {
    return ProjectView.getInstance(myProject).isAbbreviatePackageNames(myId);
  }

  public boolean isShowLibraryContents() {
    return ProjectView.getInstance(myProject).isShowLibraryContents(myId);
  }

  public boolean isShowModules() {
    return ProjectView.getInstance(myProject).isShowModules(myId);
  }
}