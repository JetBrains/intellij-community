/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.hierarchy;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.hierarchy.treestructures.PySubTypesHierarchyTreeStructure;
import com.jetbrains.python.hierarchy.treestructures.PySuperTypesHierarchyTreeStructure;
import com.jetbrains.python.hierarchy.treestructures.PyTypeHierarchyTreeStructure;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;
import java.util.Map;

public class PyTypeHierarchyBrowser extends TypeHierarchyBrowserBase {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.hierarchy.TypeHierarchyBrowser");

  protected PyTypeHierarchyBrowser(@NotNull PyClass pyClass) {
    super(pyClass.getProject(), pyClass);
  }

  @Nullable
  protected PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
    if (!(descriptor instanceof PyHierarchyNodeDescriptor)) {
      return null;
    }
    return ((PyHierarchyNodeDescriptor)descriptor).getPsiElement();
  }

  protected void createTrees(@NotNull Map<String, JTree> trees) {
    createTreeAndSetupCommonActions(trees, "PyTypeHierarchyPopupMenu");
  }

  @Nullable
  protected JPanel createLegendPanel() {
    return null;
  }

  protected boolean isApplicableElement(@NotNull PsiElement element) {
    return (element instanceof PyClass);
  }

  @Nullable
  protected HierarchyTreeStructure createHierarchyTreeStructure(@NotNull String typeName, @NotNull PsiElement psiElement) {
    if (SUPERTYPES_HIERARCHY_TYPE.equals(typeName)) {
      return new PySuperTypesHierarchyTreeStructure((PyClass)psiElement);
    }
    else if (SUBTYPES_HIERARCHY_TYPE.equals(typeName)) {
      return new PySubTypesHierarchyTreeStructure((PyClass)psiElement);
    }
    else if (TYPE_HIERARCHY_TYPE.equals(typeName)) {
      return new PyTypeHierarchyTreeStructure((PyClass)psiElement);
    }
    else {
      LOG.error("unexpected type: " + typeName);
      return null;
    }
  }

  @Nullable
  protected Comparator<NodeDescriptor> getComparator() {
    return PyHierarchyUtils.getComparator(myProject);
  }

  protected boolean isInterface(@NotNull PsiElement psiElement) {
    return false;
  }

  protected boolean canBeDeleted(PsiElement psiElement) {
    return (psiElement instanceof PyClass);
  }

  @NotNull
  protected String getQualifiedName(PsiElement psiElement) {
    if (psiElement instanceof PyClass) {
      final String name = ((PyClass)psiElement).getName();
      if (name != null) {
        return name;
      }
    }
    return "";
  }
}
