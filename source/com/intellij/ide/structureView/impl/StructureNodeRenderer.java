package com.intellij.ide.structureView.impl;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.ui.util.CellAppearance;
import com.intellij.openapi.roots.ui.util.CellAppearanceUtils;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.roots.ui.util.ModifiableCellAppearance;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

public class StructureNodeRenderer extends ColoredTreeCellRenderer {
  public void customizeCellRenderer(
      JTree tree,
      Object value,
      boolean selected,
      boolean expanded,
      boolean leaf,
      int row,
      boolean hasFocus
      ) {
    forNodeDescriptorInTree(value, expanded).customize(this);
  }

  public static CellAppearance forNodeDescriptorInTree(Object node, boolean expanded) {
    NodeDescriptor descriptor = getNodeDescriptor(node);
    if (descriptor == null) return CellAppearanceUtils.EMPTY;
    String name = descriptor.toString();
    Object psiElement = descriptor.getElement();
    ModifiableCellAppearance result;
    if (psiElement instanceof PsiElement && !((PsiElement)psiElement).isValid()) {
      result = CompositeAppearance.single(name);
    } else {
      PsiClass psiClass = getContainingClass(psiElement);
      if (isInheritedMember(node, psiClass) && psiClass != null) {
        CompositeAppearance.DequeEnd ending = new CompositeAppearance().getEnding();
        ending.addText(name, applyDeprecation(psiElement, SimpleTextAttributes.DARK_TEXT));
        ending.addComment(psiClass.getName(), applyDeprecation(psiClass, SimpleTextAttributes.GRAY_ATTRIBUTES));
        result = ending.getAppearance();
      }
      else {
        SimpleTextAttributes textAttributes = applyDeprecation(psiElement, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        result = CompositeAppearance.single(name, textAttributes);
      }
    }

    Icon icon = expanded ? descriptor.getOpenIcon() : descriptor.getClosedIcon();
    result.setIcon(icon);
    return result;
  }

  public static CellAppearance forElementInClass(PsiMember psiMember, PsiClass psiClass) {
    boolean isOwnMethod = psiMember.getContainingClass().getQualifiedName().equals(psiClass.getQualifiedName());
    String name = getNameOf(psiMember);
    psiMember.getIcon(Iconable.ICON_FLAG_VISIBILITY);
    if (isOwnMethod) {
      return CompositeAppearance.single(name, applyDeprecation(psiMember, SimpleTextAttributes.REGULAR_ATTRIBUTES));
    } else {
      CompositeAppearance.DequeEnd ending = new CompositeAppearance().getEnding();
      ending.addText(name, applyDeprecation(psiMember, SimpleTextAttributes.DARK_TEXT));
      ending.addComment(psiClass.getName(), applyDeprecation(psiClass, SimpleTextAttributes.GRAY_ATTRIBUTES));
      return ending.getAppearance();
    }
  }

  public static String getNameOf(PsiElement psiElement) {
    if (psiElement instanceof PsiMethod)
      return PsiFormatUtil.formatMethod((PsiMethod)psiElement,
                                        PsiSubstitutor.EMPTY,
                                        PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER |
                                        PsiFormatUtil.SHOW_PARAMETERS,
                                        PsiFormatUtil.SHOW_TYPE
      );
    return psiElement.toString();
  }


  private static boolean isInheritedMember(Object node, PsiClass psiClass) {
    PsiClass treeParentClass = getTreeParentClass(node);
    return treeParentClass != psiClass;
  }

  public static SimpleTextAttributes applyDeprecation(Object value, SimpleTextAttributes nameAttributes) {
    return isDeprecated(value) ? makeStrikeout(nameAttributes) : nameAttributes;
  }

  private static SimpleTextAttributes makeStrikeout(SimpleTextAttributes nameAttributes) {
    return new SimpleTextAttributes(nameAttributes.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT, nameAttributes.getFgColor());
  }

  public static boolean isDeprecated(Object psiElement) {
    if (psiElement instanceof PsiDocCommentOwner)
      return ((PsiDocCommentOwner) psiElement).isDeprecated();
    return false;
  }

  public static PsiClass getContainingClass(Object element) {
    if (element instanceof PsiMember)
      return ((PsiMember) element).getContainingClass();
    if (element instanceof PsiClass) {
      PsiElement parent = ((PsiClass) element).getParent();
      return (PsiClass) (parent instanceof PsiClass ? parent : null);
    }
    return null;
  }

  public static PsiClass getTreeParentClass(Object value) {
    if (!(value instanceof TreeNode))
      return null;
    for (TreeNode treeNode = ((TreeNode) value).getParent(); treeNode != null; treeNode = treeNode.getParent()) {
      Object element = getElement(treeNode);
      if (element instanceof PsiClass)
        return (PsiClass) element;
    }
    return null;
  }

  private static NodeDescriptor getNodeDescriptor(Object value) {
    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        return (NodeDescriptor)userObject;
      }
    }
    return null;
  }

  private static Object getElement(Object node) {
    NodeDescriptor descriptor = getNodeDescriptor(node);
    return descriptor == null ? null : descriptor.getElement();
  }
}
