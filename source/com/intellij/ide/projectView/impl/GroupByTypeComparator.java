package com.intellij.ide.projectView.impl;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.lang.properties.projectView.ResourceBundleNode;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementBase;
import com.intellij.openapi.fileTypes.StdFileTypes;

import java.util.Comparator;
import java.util.Collection;

/**
 * @author cdr
 */
public abstract class GroupByTypeComparator implements Comparator<NodeDescriptor> {
  public int compare(NodeDescriptor o1, NodeDescriptor o2) {
    if (!isSortByType() && o1 instanceof ResourceBundleNode) {
      final Collection<AbstractTreeNode> children = ((ResourceBundleNode)o1).getChildren();
      if (!children.isEmpty()) {
        o1 = children.iterator().next();
        o1.update();
      }
    }
    if (!isSortByType() && o2 instanceof ResourceBundleNode) {
      final Collection<AbstractTreeNode> children = ((ResourceBundleNode)o2).getChildren();
      if (!children.isEmpty()) {
        o2 = children.iterator().next();
        o2.update();
      }
    }
    if (o1 instanceof PsiDirectoryNode != o2 instanceof PsiDirectoryNode) {
      return o1 instanceof PsiDirectoryNode ? -1 : 1;
    }
    if (o1 instanceof PackageElementNode != o2 instanceof PackageElementNode) {
      return o1 instanceof PackageElementNode ? -1 : 1;
    }
    if (isSortByType() && o1 instanceof ClassTreeNode != o2 instanceof ClassTreeNode) {
      return o1 instanceof ClassTreeNode ? -1 : 1;
    }
    if (isSortByType() && o1 instanceof ClassTreeNode && o2 instanceof ClassTreeNode) {
      final PsiClass aClass1 = ((ClassTreeNode)o1).getValue();
      final PsiClass aClass2 = ((ClassTreeNode)o2).getValue();
      int pos1 = getClassPosition(aClass1);
      int pos2 = getClassPosition(aClass2);
      final int result = pos1 - pos2;
      if (result != 0) return result;
    }
    else if (isSortByType()
             && o1 instanceof AbstractTreeNode
             && o2 instanceof AbstractTreeNode
             && (o1 instanceof PsiFileNode || ((AbstractTreeNode)o1).getValue() instanceof ResourceBundle)
             && (o2 instanceof PsiFileNode || ((AbstractTreeNode)o2).getValue() instanceof ResourceBundle)) {
      String type1 = o1 instanceof PsiFileNode ? extension(((PsiFileNode)o1).getValue()) : StdFileTypes.PROPERTIES.getDefaultExtension();
      String type2 = o2 instanceof PsiFileNode ? extension(((PsiFileNode)o2).getValue()) : StdFileTypes.PROPERTIES.getDefaultExtension();
      if (type1 != null && type2 != null) {
        int result = type1.compareTo(type2);
        if (result != 0) return result;
      }
    }
    if (isAbbreviatePackageNames()){
      if (o1 instanceof PsiDirectoryNode) {
        final PsiDirectory aDirectory1 = ((PsiDirectoryNode)o1).getValue();
        final PsiDirectory aDirectory2 = ((PsiDirectoryNode)o2).getValue();
        if (aDirectory1 != null &&
            aDirectory2 != null) {
          final PsiPackage aPackage1 = aDirectory1.getPackage();
          final PsiPackage aPackage2 = aDirectory2.getPackage();
          if (aPackage1 != null && aPackage2 != null){
            return aPackage1.getQualifiedName().compareToIgnoreCase(aPackage2.getQualifiedName());
          }
        }
      } else if (o1 instanceof PackageElementNode) {
        final PackageElement packageElement1 = ((PackageElementNode)o1).getValue();
        final PackageElement packageElement2 = ((PackageElementNode)o2).getValue();
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
    return AlphaComparator.INSTANCE.compare(o1, o2);
  }

  protected abstract boolean isSortByType();

  protected boolean isAbbreviatePackageNames(){
    return false;
  }

  private static int getClassPosition(final PsiClass aClass) {
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
