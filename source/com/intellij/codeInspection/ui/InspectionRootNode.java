package com.intellij.codeInspection.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;
import com.intellij.codeInspection.InspectionsBundle;

import javax.swing.*;

/**
 * @author max
 */
public class InspectionRootNode extends InspectionTreeNode {
  private static final Icon INFO = IconLoader.getIcon("/general/ijLogo.png");
  private Project myProject;

  public InspectionRootNode(Project project) {
    super(project);
    myProject = project;
  }

  public String toString() {
    final VirtualFile projectFile = myProject.getProjectFile();
    return isEmpty() ? InspectionsBundle.message("inspection.empty.root.node.text") :
           projectFile != null ? projectFile.getName() : myProject.getProjectFilePath();
  }

  private boolean isEmpty() {
    return getChildCount() == 0;
  }

  public Icon getIcon(boolean expanded) {
    return isEmpty() ? INFO : Icons.PROJECT_ICON;
  }
}
