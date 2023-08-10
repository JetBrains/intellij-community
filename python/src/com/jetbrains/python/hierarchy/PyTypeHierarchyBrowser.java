// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.hierarchy;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.hierarchy.treestructures.PySubTypesHierarchyTreeStructure;
import com.jetbrains.python.hierarchy.treestructures.PySuperTypesHierarchyTreeStructure;
import com.jetbrains.python.hierarchy.treestructures.PyTypeHierarchyTreeStructure;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;
import java.util.Map;

public class PyTypeHierarchyBrowser extends TypeHierarchyBrowserBase {
  private static final Logger LOG = Logger.getInstance(PyTypeHierarchyBrowser.class);

  protected PyTypeHierarchyBrowser(@NotNull PyClass pyClass) {
    super(pyClass.getProject(), pyClass);
  }

  @Override
  @Nullable
  protected PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
    if (!(descriptor instanceof PyHierarchyNodeDescriptor)) {
      return null;
    }
    return descriptor.getPsiElement();
  }

  @Override
  protected void createTrees(@NotNull Map<? super @Nls String, ? super JTree> trees) {
    createTreeAndSetupCommonActions(trees, IdeActions.GROUP_TYPE_HIERARCHY_POPUP);
  }

  @Override
  @Nullable
  protected JPanel createLegendPanel() {
    return null;
  }

  @Override
  protected boolean isApplicableElement(@NotNull PsiElement element) {
    return (element instanceof PyClass);
  }

  @Override
  @Nullable
  protected HierarchyTreeStructure createHierarchyTreeStructure(@NotNull String typeName, @NotNull PsiElement psiElement) {
    if (getSupertypesHierarchyType().equals(typeName)) {
      return new PySuperTypesHierarchyTreeStructure((PyClass)psiElement);
    }
    else if (getSubtypesHierarchyType().equals(typeName)) {
      return new PySubTypesHierarchyTreeStructure((PyClass)psiElement);
    }
    else if (getTypeHierarchyType().equals(typeName)) {
      return new PyTypeHierarchyTreeStructure((PyClass)psiElement);
    }
    else {
      LOG.error("unexpected type: " + typeName);
      return null;
    }
  }

  @Override
  @Nullable
  protected Comparator<NodeDescriptor<?>> getComparator() {
    return PyHierarchyUtils.getComparator(myProject);
  }

  @Override
  protected boolean isInterface(@NotNull PsiElement psiElement) {
    return false;
  }

  @Override
  protected boolean canBeDeleted(PsiElement psiElement) {
    return (psiElement instanceof PyClass);
  }

  @Override
  @NotNull
  protected String getQualifiedName(PsiElement psiElement) {
    if (psiElement instanceof PyClass) {
      String name = ((PyClass)psiElement).getName();
      if (name != null) {
        return name;
      }
    }
    return "";
  }
}
