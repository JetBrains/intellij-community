/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.impl.ProjectPaneSelectInTarget;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.TreeViewUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public final class ProjectViewPane extends AbstractProjectViewPSIPane {
  @NonNls public static final String ID = "ProjectPane";
  public static final Icon ICON = IconLoader.getIcon("/general/projectTab.png");

  public ProjectViewPane(Project project, SelectInManager selectInManager) {
    super(project);
  }

  public String getTitle() {
    return IdeBundle.message("title.project");
  }

  @NotNull
  public String getId() {
    return ID;
  }

  public Icon getIcon() {
    return ICON;
  }


  public SelectInTarget createSelectInTarget() {
    return new ProjectPaneSelectInTarget(myProject);
  }

  protected AbstractTreeUpdater createTreeUpdater(AbstractTreeBuilder treeBuilder) {
    return new ProjectViewTreeUpdater(treeBuilder);
  }

  protected ProjectAbstractTreeStructureBase createStructure() {
    return new ProjectTreeStructure(myProject, ID){
      protected AbstractTreeNode createRoot(final Project project, ViewSettings settings) {
        return new ProjectViewProjectNode(project, settings);
      }
    };
  }

  protected ProjectViewTree createTree(DefaultTreeModel treeModel) {
    return new ProjectViewTree(treeModel) {
      public String toString() {
        return getTitle() + " " + super.toString();
      }

      public DefaultMutableTreeNode getSelectedNode() {
        return ProjectViewPane.this.getSelectedNode();
      }
    };
  }

  public String getComponentName() {
    return "ProjectPane";
  }


  // should be first
  public int getWeight() {
    return 0;
  }

  private final class ProjectViewTreeUpdater extends AbstractTreeUpdater {
    private ProjectViewTreeUpdater(final AbstractTreeBuilder treeBuilder) {
      super(treeBuilder);
    }

    public boolean addSubtreeToUpdateByElement(Object element) {
      if (element instanceof PsiDirectory) {
        final PsiDirectory dir = (PsiDirectory)element;
        final ProjectTreeStructure treeStructure = (ProjectTreeStructure)myTreeStructure;
        PsiDirectory dirToUpdateFrom = dir;
        if (!treeStructure.isFlattenPackages() && treeStructure.isHideEmptyMiddlePackages()) {
          // optimization: this check makes sense only if flattenPackages == false && HideEmptyMiddle == true
          while (dirToUpdateFrom != null && dirToUpdateFrom.getPackage() != null && TreeViewUtil.isEmptyMiddlePackage(dirToUpdateFrom, true)) {
            dirToUpdateFrom = dirToUpdateFrom.getParentDirectory();
          }
        }
        boolean addedOk;
        while (!(addedOk = super.addSubtreeToUpdateByElement(dirToUpdateFrom == null? myTreeStructure.getRootElement() : dirToUpdateFrom))) {
          if (dirToUpdateFrom == null) {
            break;
          }
          dirToUpdateFrom = dirToUpdateFrom.getParentDirectory();
        }
        return addedOk;
      }

      return super.addSubtreeToUpdateByElement(element);
    }

  }
}