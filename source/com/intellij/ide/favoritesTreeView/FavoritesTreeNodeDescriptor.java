package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;

/**
 * User: anna
 * Date: Feb 15, 2005
 */
public class FavoritesTreeNodeDescriptor extends NodeDescriptor<AbstractTreeNode> {
  private AbstractTreeNode myElement;
  public static final FavoritesTreeNodeDescriptor[] EMPTY_ARRAY = new FavoritesTreeNodeDescriptor[0];

  public FavoritesTreeNodeDescriptor(final Project project, final NodeDescriptor parentDescriptor, final AbstractTreeNode element) {
    super(project, parentDescriptor);
    myElement = element;
  }

  public boolean update() {
    myElement.update();
    ItemPresentation presentation = myElement.getPresentation();
    myOpenIcon = presentation.getIcon(true);
    myClosedIcon = presentation.getIcon(false);
    myName = presentation.getPresentableText();
    myColor = myElement.getFileStatus().getColor();
    return true;
  }

  protected boolean isMarkReadOnly() {
    final Object parentValue = myElement.getParent() == null ? null : myElement.getParent().getValue();
    return parentValue instanceof PsiDirectory || parentValue instanceof PackageElement;
  }

  public String getLocation(){
    Object nodeElement = myElement.getValue();
    if (nodeElement instanceof SmartPsiElementPointer){
      nodeElement = ((SmartPsiElementPointer)nodeElement).getElement();
    }
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
      final PsiFile containingFile = nodeElement instanceof PsiFile ? (PsiFile)nodeElement : parent.getContainingFile();
      return containingFile != null ? containingFile.getVirtualFile().getPresentableUrl() : "";
    }
    if (nodeElement instanceof PackageElement){
      final PackageElement packageElement = ((PackageElement)nodeElement);
      final Module module = packageElement.getModule();
      return (module != null ? (module.getName() + ":") : "") + packageElement.getPackage().getQualifiedName();
    }
    if (nodeElement instanceof LibraryGroupElement){
      return ((LibraryGroupElement)nodeElement).getModule().getName();
    }
    if (nodeElement instanceof NamedLibraryElement){
      final NamedLibraryElement namedLibraryElement = ((NamedLibraryElement)nodeElement);
      final LibraryGroupElement parent = namedLibraryElement.getParent();
      return parent.getModule().getName() + ":" + namedLibraryElement.getOrderEntry().getPresentableName();
    }
    final FavoriteNodeProvider[] nodeProviders = ApplicationManager.getApplication().getComponents(FavoriteNodeProvider.class);
    for(FavoriteNodeProvider provider: nodeProviders) {
      String location = provider.getElementLocation(nodeElement);
      if (location != null) return location;
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

  public FavoritesTreeNodeDescriptor getFavoritesRoot() {
    FavoritesTreeNodeDescriptor descriptor = this;
    while (descriptor.getParentDescriptor() instanceof FavoritesTreeNodeDescriptor) {
      FavoritesTreeNodeDescriptor parent = (FavoritesTreeNodeDescriptor)descriptor.getParentDescriptor();
      if (parent != null && parent.getParentDescriptor() == null) {
        return descriptor;
      }
      descriptor = parent;
    }
    return descriptor;
  }
}
