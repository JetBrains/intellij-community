/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.impl.PackagesPaneSelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.projectView.impl.nodes.PackageUtil;
import com.intellij.ide.projectView.impl.nodes.PackageViewProjectNode;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PackageViewPane extends AbstractProjectViewPSIPane {
  @NonNls public static final String ID = "PackagesPane";
  public static final Icon ICON = IconLoader.getIcon("/general/packagesTab.png");

  public PackageViewPane(Project project) {
    super(project);
  }

  public String getTitle() {
    return IdeBundle.message("title.packages");
  }

  public Icon getIcon() {
    return ICON;
  }

  @NotNull
  public String getId() {
    return ID;
  }

  public AbstractTreeStructure getTreeStructure() {
    return myTreeStructure;
  }

  private final class ShowModulesAction extends ToggleAction {
    private ShowModulesAction() {
      super(IdeBundle.message("action.show.modules"), IdeBundle.message("action.description.show.modules"), IconLoader.getIcon("/objectBrowser/showModules.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return ProjectView.getInstance(myProject).isShowModules(getId());
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);
      projectView.setShowModules(flag, getId());
    }

    public void update(AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);
      presentation.setVisible(projectView.getCurrentProjectViewPane() == PackageViewPane.this);
    }
  }

  private final class ShowLibraryContentsAction extends ToggleAction {
    private ShowLibraryContentsAction() {
      super(IdeBundle.message("action.show.libraries.contents"), IdeBundle.message("action.show.hide.library.contents"), IconLoader.getIcon("/objectBrowser/showLibraryContents.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return ProjectView.getInstance(myProject).isShowLibraryContents(getId());
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);
      projectView.setShowLibraryContents(flag, getId());
    }

    public void update(AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);
      presentation.setVisible(projectView.getCurrentProjectViewPane() == PackageViewPane.this);
    }
  }

  public void addToolbarActions(DefaultActionGroup actionGroup) {
    actionGroup.add(new ShowModulesAction());
    actionGroup.add(new ShowLibraryContentsAction());
  }

  protected AbstractTreeUpdater createTreeUpdater(AbstractTreeBuilder treeBuilder) {
    return new PackageViewTreeUpdater(treeBuilder);
  }

  public SelectInTarget createSelectInTarget() {
    return new PackagesPaneSelectInTarget(myProject);
  }

  protected ProjectAbstractTreeStructureBase createStructure() {
    return new ProjectTreeStructure(myProject, ID){
      protected AbstractTreeNode createRoot(final Project project, ViewSettings settings) {
        return new PackageViewProjectNode(project, settings);
      }
    };
  }

  protected ProjectViewTree createTree(DefaultTreeModel treeModel) {
    return new ProjectViewTree(treeModel) {
      public String toString() {
        return getTitle() + " " + super.toString();
      }

      public DefaultMutableTreeNode getSelectedNode() {
        return PackageViewPane.this.getSelectedNode();
      }
    };
  }

  @NotNull
  public String getComponentName() {
    return "PackagesPane";
  }

  public int getWeight() {
    return 1;
  }

  private final class PackageViewTreeUpdater extends AbstractTreeUpdater {
    private PackageViewTreeUpdater(final AbstractTreeBuilder treeBuilder) {
      super(treeBuilder);
    }

    public boolean addSubtreeToUpdateByElement(Object element) {
      // should convert PsiDirectories into PackageElements
      if (element instanceof PsiDirectory) {
        PsiDirectory dir = (PsiDirectory)element;
        final PsiPackage aPackage = dir.getPackage();
        if (ProjectView.getInstance(myProject).isShowModules(getId())) {
          Module[] modules = getModulesFor(dir);
          boolean rv = false;
          for (Module module : modules) {
            rv |= addPackageElementToUpdate(aPackage, module);
          }
          return rv;
        }
        else {
          return addPackageElementToUpdate(aPackage, null);
        }
      }

      return super.addSubtreeToUpdateByElement(element);
    }

    private boolean addPackageElementToUpdate(final PsiPackage aPackage, Module module) {
      final ProjectTreeStructure packageTreeStructure = (ProjectTreeStructure)myTreeStructure;
      PsiPackage packageToUpdateFrom = aPackage;
      if (!packageTreeStructure.isFlattenPackages() && packageTreeStructure.isHideEmptyMiddlePackages()) {
        // optimization: this check makes sense only if flattenPackages == false && HideEmptyMiddle == true
        while (packageToUpdateFrom != null && packageToUpdateFrom.isValid() && PackageUtil.isPackageEmpty(packageToUpdateFrom, module, true, false)) {
          packageToUpdateFrom = packageToUpdateFrom.getParentPackage();
        }
      }
      boolean addedOk;
      while (!(addedOk = super.addSubtreeToUpdateByElement(getTreeElementToUpdateFrom(packageToUpdateFrom, module)))) {
        if (packageToUpdateFrom == null) {
          break;
        }
        packageToUpdateFrom = packageToUpdateFrom.getParentPackage();
      }
      return addedOk;
    }

    private Object getTreeElementToUpdateFrom(PsiPackage packageToUpdateFrom, Module module) {
      if (packageToUpdateFrom == null || !packageToUpdateFrom.isValid() || "".equals(packageToUpdateFrom.getQualifiedName())) {
        return module == null ? myTreeStructure.getRootElement() : module;
      }
      else {
        return new PackageElement(module, packageToUpdateFrom, false);
      }
    }

    private Module[] getModulesFor(PsiDirectory dir) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      final VirtualFile vFile = dir.getVirtualFile();
      final Set<Module> modules = new HashSet<Module>();
      final Module module = fileIndex.getModuleForFile(vFile);
      if (module != null) {
        modules.add(module);
      }
      if (fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile)) {
        final List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(vFile);
        if (orderEntries.isEmpty()) {
          return Module.EMPTY_ARRAY;
        }
        for (OrderEntry entry : orderEntries) {
          modules.add(entry.getOwnerModule());
        }
      }
      return modules.toArray(new Module[modules.size()]);
    }
  }
}