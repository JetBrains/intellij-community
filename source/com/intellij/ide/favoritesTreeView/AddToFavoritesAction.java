package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.PackageViewPane;
import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.j2ee.module.view.J2EEProjectViewPane;
import com.intellij.j2ee.module.view.nodes.J2EENodeDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: Feb 15, 2005
 */
public class AddToFavoritesAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.favoritesTreeView.AddToFavoritesAction");

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final AbstractTreeNode[] nodes = createNode(dataContext);
    if (nodes != null) {
      for (int i = 0; i < nodes.length; i++) {
        AbstractTreeNode node = nodes[i];
        FavoritesViewImpl.getInstance(project).getFavoritesTreeViewPanel().addToFavorites(node);
      }
    }
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setEnabled(createNode(dataContext) != null);
    }
  }

  private AbstractTreeNode[] createNode(DataContext dataContext) {
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final FavoritesTreeViewConfiguration favoritesConfig = FavoritesTreeViewConfiguration.getInstance(project);
    final PsiManager psiManager = PsiManager.getInstance(project);

    final String currentViewId = ProjectView.getInstance(project).getCurrentViewId();
    AbstractProjectViewPane pane = ProjectView.getInstance(project).getProjectViewPaneById(currentViewId);
    if (currentViewId == J2EEProjectViewPane.ID){
      J2EENodeDescriptor j2eeNodeDescriptor = (J2EENodeDescriptor)pane.getSelectedDescriptor();
    }

    //on psi element
    PsiElement psiElement = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    if (psiElement != null){
      Module containingModule = null;
      if (ProjectView.getInstance(project).isShowModules(currentViewId)){
        AbstractTreeNode abstractTreeNode = ((AbstractTreeNode)pane.getSelectedDescriptor().getElement());
        while (abstractTreeNode != null && !(abstractTreeNode.getParent() instanceof AbstractModuleNode)){
          abstractTreeNode = abstractTreeNode.getParent();
        }
        if (abstractTreeNode != null){
          containingModule = (Module)((AbstractModuleNode)abstractTreeNode.getParent()).getValue();
        }
      }
      addPsiElementNode(psiElement, project, result, favoritesConfig, containingModule);
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }
    
    //on psi elements
    final PsiElement[] psiElements = (PsiElement[])dataContext.getData(DataConstantsEx.PSI_ELEMENT_ARRAY);
    if (psiElements != null) {
      for (int i = 0; i < psiElements.length; i++) {
        addPsiElementNode(psiElements[i], project, result, favoritesConfig, (Module)dataContext.getData(DataConstants.MODULE_CONTEXT));
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on module node
    Module module = (Module)dataContext.getData(DataConstants.MODULE);
    if (module != null) {
      if (currentViewId == PackageViewPane.ID) {
        result.add(new PackageViewModuleNode(project, module, favoritesConfig));
      }
      else {
        result.add(new ProjectViewModuleNode(project, module, favoritesConfig));
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on module nodes
    Module[] modules = (Module[])dataContext.getData(DataConstants.MODULE_CONTEXT_ARRAY);
    if (modules != null) {
      for (int i = 0; i < modules.length; i++) {
        if (currentViewId == PackageViewPane.ID) {
          result.add(new PackageViewModuleNode(project, modules[i], favoritesConfig));
        }
        else {
          result.add(new ProjectViewModuleNode(project, modules[i], favoritesConfig));
        }
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on module groups
    ModuleGroup[] moduleGroups = (ModuleGroup[])dataContext.getData(DataConstantsEx.MODULE_GROUP_ARRAY);
    if (moduleGroups != null) {
      boolean isPackageView = false;
      if (currentViewId == PackageViewPane.ID) {
        isPackageView = true;
      }
      for (int i = 0; i < moduleGroups.length; i++) {
        ModuleGroup moduleGroup = moduleGroups[i];
        if (isPackageView){
          result.add(new PackageViewModuleGroupNode(project, moduleGroup, favoritesConfig));
        } else {
          result.add(new ProjectViewModuleGroupNode(project, moduleGroup, favoritesConfig));
        }
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on form in editor
    final VirtualFile vFile = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
    if (vFile != null) {
      final FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(vFile);
      if (StdFileTypes.GUI_DESIGNER_FORM.equals(fileType)) {
        final PsiFile formFile = psiManager.findFile(vFile);
        String text = formFile.getText();
        LwRootContainer container = null;
        try {
          container = Utils.getRootContainer(text, null);
        }
        catch (Exception e) {
          return null;
        }
        final PsiClass classToBind = psiManager.findClass(container.getClassToBind(), GlobalSearchScope.allScope(project));
        result.add(FormNode.constructFormNode(psiManager, classToBind, project, favoritesConfig));
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on form nodes
    final Form[] forms = (Form[])dataContext.getData(DataConstantsEx.GUI_DESIGNER_FORM_ARRAY);
    if (forms != null) {
      Set<PsiClass> bindClasses = new HashSet<PsiClass>();
      for (int i = 0; i < forms.length; i++) {
        Form form = forms[i];
        final PsiClass classToBind = form.getClassToBind();
        if (bindClasses.contains(classToBind)) continue;
        bindClasses.add(classToBind);
        result.add(FormNode.constructFormNode(psiManager, classToBind, project, favoritesConfig));
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on library group node
    final LibraryGroupElement[] libraryGroups = (LibraryGroupElement[])dataContext.getData(DataConstantsEx.LIBRARY_GROUP_ARRAY);
    if (libraryGroups != null){
      for (int i = 0; i < libraryGroups.length; i++) {
        LibraryGroupElement libraryGroup = libraryGroups[i];
        result.add(new LibraryGroupNode(project, libraryGroup, favoritesConfig));
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }

    //on named library node
    final NamedLibraryElement[] namedLibraries = (NamedLibraryElement[])dataContext.getData(DataConstantsEx.NAMED_LIBRARY_ARRAY);
    if (namedLibraries != null){
      for (int i = 0; i < namedLibraries.length; i++) {
        NamedLibraryElement namedLibrary = namedLibraries[i];
        result.add(new NamedLibraryElementNode(project, namedLibrary, favoritesConfig));
      }
      return result.isEmpty() ? null : result.toArray(new AbstractTreeNode[result.size()]);
    }
    return null;
  }

  private void addPsiElementNode(final PsiElement psiElement,
                                 final Project project,
                                 final ArrayList<AbstractTreeNode> result,
                                 final FavoritesTreeViewConfiguration favoritesConfig,
                                 Module module) {
    final Class <? extends AbstractTreeNode> klass = getPsiElementNodeClass(psiElement);
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


  private Class<? extends AbstractTreeNode> getPsiElementNodeClass(PsiElement psiElement) {
    Class<? extends AbstractTreeNode> klass = null;
    if (psiElement instanceof PsiClass) {
      klass = ClassTreeNode.class;
    }
    else if (psiElement instanceof PsiMethod) {
      klass = PsiMethodNode.class;
    }
    else if (psiElement instanceof PsiField) {
      klass = PsiFieldNode.class;
    }
    else if (psiElement instanceof PsiFile) {
      klass = PsiFileNode.class;
    }
    else if (psiElement instanceof PsiDirectory) {
      klass = PsiDirectoryNode.class;
    } else if (psiElement instanceof PsiPackage) {
      klass = PackageElementNode.class;
    }
    return klass;
  }

  private Object getPsiElementNodeValue(PsiElement psiElement, Project project, Module module) {
    if (psiElement instanceof PsiPackage){
      final PsiPackage psiPackage = (PsiPackage)psiElement;
      final PsiDirectory[] directories = psiPackage.getDirectories();
      final VirtualFile firstDir = directories[0].getVirtualFile();
      final boolean isLibraryRoot = PackageUtil.isLibraryRoot(firstDir, project);
      return new PackageElement(module, psiPackage, isLibraryRoot);
    }
    return psiElement;
  }
}