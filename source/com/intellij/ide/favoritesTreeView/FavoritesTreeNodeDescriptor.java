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
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;

import javax.swing.*;

/**
 * User: anna
 * Date: Feb 15, 2005
 */
public class FavoritesTreeNodeDescriptor extends NodeDescriptor<AbstractTreeNode> {
  private AbstractTreeNode myElement;

  public FavoritesTreeNodeDescriptor(final Project project, final NodeDescriptor parentDescriptor, final AbstractTreeNode element) {
    super(project, parentDescriptor);
    myElement = element;
  }

  public boolean update() {
    myElement.update();
    myOpenIcon = myElement.getPresentation().getIcon(true);
    myClosedIcon = myElement.getPresentation().getIcon(false);
    myName = myElement.getPresentation().getPresentableText();
    final Object value = myElement.getValue();
    if (value instanceof PsiElement){
      if (((PsiElement)value).getContainingFile() != null){
        final VirtualFile virtualFile = ((PsiElement)value).getContainingFile().getVirtualFile();
        if (virtualFile != null){
          myColor = FileStatusManager.getInstance(myProject).getStatus(virtualFile).getColor();
        } else {
          myColor = FileStatus.NOT_CHANGED.getColor();
        }
        int flags = ((PsiElement)value).getContainingFile() instanceof PsiJavaFile  ?  Iconable.ICON_FLAG_VISIBILITY : 0;
        if (isMarkReadOnly()) {
          flags |= Iconable.ICON_FLAG_READ_STATUS;
        }
        Icon icon = ((PsiElement)value).getIcon(flags);
        myOpenIcon = icon;
        myClosedIcon = icon;
      }

    }
    return true;
  }

  protected boolean isMarkReadOnly() {
    final Object parentValue = myElement.getParent() == null ? null : myElement.getParent().getValue();
    return parentValue instanceof PsiDirectory || parentValue instanceof PackageElement;
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
      if (parent == null) return "";
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

  public static FavoritesTreeNodeDescriptor getFavoritesRoot(FavoritesTreeNodeDescriptor node, Project project, String favoritesViewPane) {
    final FavoritesTreeViewPanel favoritesTreeViewPanel = FavoritesViewImpl.getInstance(project).getFavoritesTreeViewPanel(favoritesViewPane);
    while (node.getParentDescriptor() != null && node.getParentDescriptor() instanceof FavoritesTreeNodeDescriptor) {
      FavoritesTreeNodeDescriptor favoritesDescriptor = (FavoritesTreeNodeDescriptor)node.getParentDescriptor();
      if (favoritesDescriptor.getElement() == favoritesTreeViewPanel.getFavoritesTreeStructure().getRootElement()) {
        return node;
      }
      node = favoritesDescriptor;
    }
    return node;
  }
}
