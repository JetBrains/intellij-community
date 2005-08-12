package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.favoritesTreeView.smartPointerPsiNodes.ClassSmartPointerNode;
import com.intellij.ide.favoritesTreeView.smartPointerPsiNodes.FieldSmartPointerNode;
import com.intellij.ide.favoritesTreeView.smartPointerPsiNodes.MethodSmartPointerNode;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.PackageViewPane;
import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.projectView.ResourceBundleNode;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uiDesigner.compiler.Utils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: Feb 15, 2005
 */
public class AddToFavoritesAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.favoritesTreeView.AddToFavoritesAction");

  private String myFavoritesList;

  public AddToFavoritesAction(String choosenList) {
    super(choosenList);
    myFavoritesList = choosenList;
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final AbstractTreeNode[] nodes = createNodes(dataContext, e.getPlace().equals(ActionPlaces.J2EE_VIEW_POPUP) ||
                                                              e.getPlace().equals(ActionPlaces.STRUCTURE_VIEW_POPUP) ||
                                                              e.getPlace().equals(ActionPlaces.PROJECT_VIEW_POPUP));
    addNodes(project, nodes);
  }

  public void addNodes(final Project project, final AbstractTreeNode[] nodes) {
    final FavoritesViewImpl favoritesView = FavoritesViewImpl.getInstance(project);
    if (nodes != null) {
      final FavoritesTreeViewPanel favoritesTreeViewPanel = favoritesView.getFavoritesTreeViewPanel(myFavoritesList);
      for (AbstractTreeNode node : nodes) {
        favoritesTreeViewPanel.addToFavorites(node);
      }
      final ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
      windowManager.getToolWindow(ToolWindowId.FAVORITES_VIEW).activate(null);
      favoritesView.setSelectedContent(favoritesView.getContent(favoritesTreeViewPanel));
    }
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setEnabled(createNodes(dataContext, e.getPlace().equals(ActionPlaces.J2EE_VIEW_POPUP) ||
                                                              e.getPlace().equals(ActionPlaces.STRUCTURE_VIEW_POPUP) ||
                                                              e.getPlace().equals(ActionPlaces.PROJECT_VIEW_POPUP)) != null);
    }
  }

  @Nullable
  private AbstractTreeNode[] createNodes(DataContext dataContext, boolean inProjectView) {
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final ViewSettings favoritesConfig = FavoritesViewImpl.getInstance(project).getFavoritesTreeViewPanel(myFavoritesList)
      .getFavoritesTreeStructure().getFavoritesConfiguration();
    return createNodes(dataContext, inProjectView, favoritesConfig);
  }

  public static
  @Nullable
  AbstractTreeNode[] createNodes(DataContext dataContext, boolean inProjectView, ViewSettings favoritesConfig) {
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return null;
    Module module = (Module)dataContext.getData(DataConstants.MODULE);
    final PsiManager psiManager = PsiManager.getInstance(project);

    final String currentViewId = ProjectView.getInstance(project).getCurrentViewId();
    AbstractProjectViewPane pane = ProjectView.getInstance(project).getProjectViewPaneById(currentViewId);

    //on bundles nodes
    final ResourceBundle[] bundles = (ResourceBundle[])dataContext.getData(DataConstantsEx.RESOURCE_BUNDLE_ARRAY);
    if (bundles != null) {
      for (ResourceBundle bundle : bundles) {
        result.add(new ResourceBundleNode(project, bundle, favoritesConfig));
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on psi elements
    final PsiElement[] psiElements = (PsiElement[])dataContext.getData(DataConstantsEx.PSI_ELEMENT_ARRAY);
    if (psiElements != null) {
      for (PsiElement psiElement : psiElements) {
        addPsiElementNode(psiElement, project, result, favoritesConfig, (Module)dataContext.getData(DataConstants.MODULE_CONTEXT));
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on psi element
    PsiElement psiElement = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    if (psiElement == null && dataContext.getData(DataConstants.PSI_FILE) != null) {
      psiElement = (PsiElement)dataContext.getData(DataConstants.PSI_FILE);
    }
    if (psiElement != null) {
      Module containingModule = null;
      if (inProjectView && ProjectView.getInstance(project).isShowModules(currentViewId)) {
        if (pane.getSelectedDescriptor() != null && pane.getSelectedDescriptor().getElement() instanceof AbstractTreeNode) {
          AbstractTreeNode abstractTreeNode = ((AbstractTreeNode)pane.getSelectedDescriptor().getElement());
          while (abstractTreeNode != null && !(abstractTreeNode.getParent() instanceof AbstractModuleNode)) {
            abstractTreeNode = abstractTreeNode.getParent();
          }
          if (abstractTreeNode != null) {
            containingModule = ((AbstractModuleNode)abstractTreeNode.getParent()).getValue();
          }
        }
      }
      addPsiElementNode(psiElement, project, result, favoritesConfig, containingModule);
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    final VirtualFile[] vFiles = (VirtualFile[])dataContext.getData(DataConstantsEx.VIRTUAL_FILE_ARRAY);
    if (vFiles != null) {
      for (VirtualFile vFile : vFiles) {
        final PsiFile psiFile = psiManager.findFile(vFile);
        addPsiElementNode(psiFile,
                          project,
                          result,
                          favoritesConfig,
                          (Module)dataContext.getData(DataConstants.MODULE_CONTEXT));
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on form in editor
    final VirtualFile vFile = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
    if (vFile != null) {
      final FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(vFile);
      if (StdFileTypes.GUI_DESIGNER_FORM.equals(fileType)) {
        final PsiFile formFile = psiManager.findFile(vFile);
        if (formFile == null) return null;
        String text = formFile.getText();
        String className;
        try {
          className = Utils.getBoundClassName(text);
        }
        catch (Exception e) {
          return null;
        }
        final PsiClass classToBind = psiManager.findClass(className, GlobalSearchScope.allScope(project));
        if (classToBind != null) {
          result.add(FormNode.constructFormNode(psiManager, classToBind, project, favoritesConfig));
        }
        else {
          addPsiElementNode(formFile, project, result, favoritesConfig, module);
        }
      }
      else {
        final PsiFile psiFile = psiManager.findFile(vFile);
        addPsiElementNode(psiFile,
                          project,
                          result,
                          favoritesConfig,
                          (Module)dataContext.getData(DataConstants.MODULE_CONTEXT));
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on form nodes
    final Form[] forms = (Form[])dataContext.getData(DataConstantsEx.GUI_DESIGNER_FORM_ARRAY);
    if (forms != null) {
      Set<PsiClass> bindClasses = new HashSet<PsiClass>();
      for (Form form : forms) {
        final PsiClass classToBind = form.getClassToBind();
        if (classToBind != null) {
          if (bindClasses.contains(classToBind)) continue;
          bindClasses.add(classToBind);
          result.add(FormNode.constructFormNode(psiManager, classToBind, project, favoritesConfig));
        }
        else {
          //can't be on FormNodes
        }
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on module groups
    ModuleGroup[] moduleGroups = (ModuleGroup[])dataContext.getData(DataConstantsEx.MODULE_GROUP_ARRAY);
    if (moduleGroups != null) {
      boolean isPackageView = false;
      if (currentViewId.equals(PackageViewPane.ID)) {
        isPackageView = true;
      }
      for (ModuleGroup moduleGroup : moduleGroups) {
        if (isPackageView) {
          result.add(new PackageViewModuleGroupNode(project, moduleGroup, favoritesConfig));
        }
        else {
          result.add(new ProjectViewModuleGroupNode(project, moduleGroup, favoritesConfig));
        }
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on module nodes
    Module[] modules = (Module[])dataContext.getData(DataConstants.MODULE_CONTEXT_ARRAY);
    if (modules != null) {
      for (Module module1 : modules) {
        if (currentViewId.equals(PackageViewPane.ID)) {
          result.add(new PackageViewModuleNode(project, module1, favoritesConfig));
        }
        else {
          result.add(new ProjectViewModuleNode(project, module1, favoritesConfig));
        }
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on module node
    if (module != null) {
      if (currentViewId.equals(PackageViewPane.ID)) {
        result.add(new PackageViewModuleNode(project, module, favoritesConfig));
      }
      else {
        result.add(new ProjectViewModuleNode(project, module, favoritesConfig));
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on library group node
    final LibraryGroupElement[] libraryGroups = (LibraryGroupElement[])dataContext.getData(DataConstantsEx.LIBRARY_GROUP_ARRAY);
    if (libraryGroups != null) {
      for (LibraryGroupElement libraryGroup : libraryGroups) {
        result.add(new LibraryGroupNode(project, libraryGroup, favoritesConfig));
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on named library node
    final NamedLibraryElement[] namedLibraries = (NamedLibraryElement[])dataContext.getData(DataConstantsEx.NAMED_LIBRARY_ARRAY);
    if (namedLibraries != null) {
      for (NamedLibraryElement namedLibrary : namedLibraries) {
        result.add(new NamedLibraryElementNode(project, namedLibrary, favoritesConfig));
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }
    return null;
  }

  public static void addPsiElementNode(PsiElement psiElement,
                                       final Project project,
                                       final ArrayList<AbstractTreeNode> result,
                                       final ViewSettings favoritesConfig,
                                       Module module) {
    Class<? extends AbstractTreeNode> klass = getPsiElementNodeClass(psiElement);
    if (klass == null) {
      psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiFile.class);
      if (psiElement != null) {
        klass = PsiFileNode.class;
      }
    }
    final Object value = getPsiElementNodeValue(psiElement, project, module);
    try {
      if (klass != null && value != null) {
        result.add(ProjectViewNode.createTreeNode(klass, project, value, favoritesConfig));
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }


  private static Class<? extends AbstractTreeNode> getPsiElementNodeClass(PsiElement psiElement) {
    Class<? extends AbstractTreeNode> klass = null;
    if (psiElement instanceof PsiClass) {
      klass = ClassSmartPointerNode.class;
    }
    else if (psiElement instanceof PsiMethod) {
      klass = MethodSmartPointerNode.class;
    }
    else if (psiElement instanceof PsiField) {
      klass = FieldSmartPointerNode.class;
    }
    else if (psiElement instanceof PsiFile) {
      klass = PsiFileNode.class;
    }
    else if (psiElement instanceof PsiDirectory) {
      klass = PsiDirectoryNode.class;
    }
    else if (psiElement instanceof PsiPackage) {
      klass = PackageElementNode.class;
    }
    return klass;
  }

  private static Object getPsiElementNodeValue(PsiElement psiElement, Project project, Module module) {
    if (psiElement instanceof PsiPackage) {
      final PsiPackage psiPackage = (PsiPackage)psiElement;
      final PsiDirectory[] directories = psiPackage.getDirectories();
      if (directories.length > 0) {
        final VirtualFile firstDir = directories[0].getVirtualFile();
        final boolean isLibraryRoot = PackageUtil.isLibraryRoot(firstDir, project);
        return new PackageElement(module, psiPackage, isLibraryRoot);
      }
    }
    return psiElement;
  }

}