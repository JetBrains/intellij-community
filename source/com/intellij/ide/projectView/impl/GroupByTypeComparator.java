package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.projectView.ResourceBundleNode;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementBase;

import java.util.Collection;
import java.util.Comparator;

/**
 * @author cdr
 */
public abstract class GroupByTypeComparator implements Comparator<NodeDescriptor> {
  public int compare(NodeDescriptor descriptor1, NodeDescriptor descriptor2) {
    if (!isSortByType() && descriptor1 instanceof ResourceBundleNode) {
      final Collection<AbstractTreeNode> children = ((ResourceBundleNode)descriptor1).getChildren();
      if (!children.isEmpty()) {
        descriptor1 = children.iterator().next();
        descriptor1.update();
      }
    }
    if (!isSortByType() && descriptor2 instanceof ResourceBundleNode) {
      final Collection<AbstractTreeNode> children = ((ResourceBundleNode)descriptor2).getChildren();
      if (!children.isEmpty()) {
        descriptor2 = children.iterator().next();
        descriptor2.update();
      }
    }
    if (descriptor1 instanceof ModuleGroupNode != descriptor2 instanceof ModuleGroupNode) {
      return descriptor1 instanceof ModuleGroupNode ? -1 : 1;
    }
    if (descriptor1 instanceof ProjectViewModuleNode != descriptor2 instanceof ProjectViewModuleNode) {
      return descriptor1 instanceof ProjectViewModuleNode ? -1 : 1;
    }
    if (descriptor1 instanceof PsiDirectoryNode != descriptor2 instanceof PsiDirectoryNode) {
      return descriptor1 instanceof PsiDirectoryNode ? -1 : 1;
    }
    if (descriptor1 instanceof PackageElementNode != descriptor2 instanceof PackageElementNode) {
      return descriptor1 instanceof PackageElementNode ? -1 : 1;
    }
    if (isSortByType() && descriptor1 instanceof ClassTreeNode != descriptor2 instanceof ClassTreeNode) {
      return descriptor1 instanceof ClassTreeNode ? -1 : 1;
    }
    if (isSortByType() && descriptor1 instanceof ClassTreeNode && descriptor2 instanceof ClassTreeNode) {
      final PsiClass aClass1 = ((ClassTreeNode)descriptor1).getValue();
      final PsiClass aClass2 = ((ClassTreeNode)descriptor2).getValue();
      int pos1 = getClassPosition(aClass1);
      int pos2 = getClassPosition(aClass2);
      final int result = pos1 - pos2;
      if (result != 0) return result;
    }
    else if (isSortByType()
             && descriptor1 instanceof AbstractTreeNode
             && descriptor2 instanceof AbstractTreeNode
             && (descriptor1 instanceof PsiFileNode || ((AbstractTreeNode)descriptor1).getValue() instanceof ResourceBundle)
             && (descriptor2 instanceof PsiFileNode || ((AbstractTreeNode)descriptor2).getValue() instanceof ResourceBundle)) {
      String type1 = descriptor1 instanceof PsiFileNode ? extension(((PsiFileNode)descriptor1).getValue()) : StdFileTypes.PROPERTIES.getDefaultExtension();
      String type2 = descriptor2 instanceof PsiFileNode ? extension(((PsiFileNode)descriptor2).getValue()) : StdFileTypes.PROPERTIES.getDefaultExtension();
      if (type1 != null && type2 != null) {
        int result = type1.compareTo(type2);
        if (result != 0) return result;
      }
    }
    if (isAbbreviatePackageNames()){
      if (descriptor1 instanceof PsiDirectoryNode) {
        final PsiDirectory aDirectory1 = ((PsiDirectoryNode)descriptor1).getValue();
        final PsiDirectory aDirectory2 = ((PsiDirectoryNode)descriptor2).getValue();
        if (aDirectory1 != null &&
            aDirectory2 != null) {
          final PsiPackage aPackage1 = aDirectory1.getPackage();
          final PsiPackage aPackage2 = aDirectory2.getPackage();
          if (aPackage1 != null && aPackage2 != null){
            return aPackage1.getQualifiedName().compareToIgnoreCase(aPackage2.getQualifiedName());
          }
        }
      } else if (descriptor1 instanceof PackageElementNode) {
        final PackageElement packageElement1 = ((PackageElementNode)descriptor1).getValue();
        final PackageElement packageElement2 = ((PackageElementNode)descriptor2).getValue();
        if (packageElement1 != null &&
            packageElement2 != null){
          final PsiPackage aPackage1 = packageElement1.getPackage();
          final PsiPackage aPackage2 = packageElement2.getPackage();
          if (aPackage1 != null && aPackage2 != null) {
            return aPackage1.getQualifiedName().compareToIgnoreCase(aPackage2.getQualifiedName());
          }
        }
      }
    }
    return AlphaComparator.INSTANCE.compare(descriptor1, descriptor2);
  }

  protected abstract boolean isSortByType();

  protected boolean isAbbreviatePackageNames(){
    return false;
  }

  public static int getClassPosition(final PsiClass aClass) {
    if (aClass == null || !aClass.isValid()) {
      return 0;
    }
    int pos = ElementBase.getClassKind(aClass);
    //abstract class before concrete
    if (pos == ElementBase.CLASS_KIND_CLASS || pos == ElementBase.CLASS_KIND_EXCEPTION) {
      boolean isAbstract = aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !aClass.isInterface();
      if (isAbstract) {
        pos --;
      }
    }
    return pos;
  }
  private static String extension(final PsiFile file) {
    return file == null || file.getVirtualFile() == null ? null : file.getVirtualFile().getFileType().getDefaultExtension();
  }
}
