package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.impl.nodes.Form;
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.presentation.java.ClassPresentationUtil;

/**
 * User: anna
 * Date: Feb 15, 2005
 */
public class FavoritesTreeNodeDescriptor extends NodeDescriptor<AbstractTreeNode> {
  private AbstractTreeNode myElement;

  public FavoritesTreeNodeDescriptor(final Project project, final NodeDescriptor parentDescriptor, final AbstractTreeNode element) {
    super(project, parentDescriptor);
    myElement = element;
    myOpenIcon = myElement.getPresentation().getIcon(true);
    myClosedIcon = myElement.getPresentation().getIcon(false);
    myName = myElement.getPresentation().getPresentableText();
  }

  public boolean update() {
    myElement.update();
    myName = myElement.getPresentation().getPresentableText();
    final Object value = myElement.getValue();
    if (value instanceof PsiElement && ((PsiElement)value).getContainingFile() != null){
      myColor = FileStatusManager.getInstance(myProject).getStatus(((PsiElement)value).getContainingFile().getVirtualFile()).getColor();
    }
    return true;
  }

  public String getLocation(){
    final Object nodeElement = myElement.getValue();
    if (nodeElement instanceof PsiElement){
      if (nodeElement instanceof PsiClass){
        return ClassPresentationUtil.getNameForClass((PsiClass)nodeElement, true);
      }
      if (nodeElement instanceof PsiDirectory){
        return ((PsiDirectory)nodeElement).getVirtualFile().getPresentableUrl();
      }
      final PsiElement parent = ((PsiElement)nodeElement).getParent();
      if (parent instanceof PsiClass){
        return ClassPresentationUtil.getNameForClass((PsiClass)parent, true);
      }
      final Module module = ModuleUtil.findModuleForPsiElement(parent);
      final PsiFile containingFile = parent.getContainingFile();
      return module != null && containingFile != null ? (module.getName() + ":" + containingFile.getName()) : "";
    }
    if (nodeElement instanceof PackageElement){
      final PackageElement packageElement = ((PackageElement)nodeElement);
      final Module module = packageElement.getModule();
      return (module != null ? (module.getName() + ":") : "") + packageElement.getPackage().getQualifiedName();
    }
    if (nodeElement instanceof Form){
      return ((Form)nodeElement).getName();
    }
    if (nodeElement instanceof LibraryGroupElement){
      return ((LibraryGroupElement)nodeElement).getModule().getName();
    }
    if (nodeElement instanceof NamedLibraryElement){
      final NamedLibraryElement namedLibraryElement = ((NamedLibraryElement)nodeElement);
      final LibraryGroupElement parent = namedLibraryElement.getParent();
      return parent.getModule().getName() + ":" + namedLibraryElement.getOrderEntry().getPresentableName();
    }
    return null;
  }

  public AbstractTreeNode getElement() {
    return myElement;
  }

  public boolean equals(Object object) {
    if (!(object instanceof FavoritesTreeNodeDescriptor)) return false;
    return ((FavoritesTreeNodeDescriptor)object).getElement().equals(myElement);
  }

  public int hashCode() {
    return myElement.hashCode();
  }
}
