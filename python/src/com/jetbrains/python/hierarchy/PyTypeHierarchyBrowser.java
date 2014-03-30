/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.ui.PopupHandler;
import com.jetbrains.python.hierarchy.treestructures.PySubTypesHierarchyTreeStructure;
import com.jetbrains.python.hierarchy.treestructures.PySuperTypesHierarchyTreeStructure;
import com.jetbrains.python.hierarchy.treestructures.PyTypeHierarchyTreeStructure;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Jul 31, 2009
 * Time: 6:14:42 PM
 */
public class PyTypeHierarchyBrowser extends TypeHierarchyBrowserBase {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.hierarchy.TypeHierarchyBrowser");

  protected PyTypeHierarchyBrowser(@NotNull PyClass pyClass) {
    super(pyClass.getProject(), pyClass);
  }

  @Nullable
  protected PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
    if (!(descriptor instanceof PyTypeHierarchyNodeDescriptor)) {
      return null;
    }
    return ((PyTypeHierarchyNodeDescriptor)descriptor).getClassElement();
  }

  protected void createTrees(@NotNull Map<String, JTree> trees) {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("PyTypeHierarchyPopupMenu");
    final BaseOnThisTypeAction baseOnThisTypeAction = new BaseOnThisTypeAction();
    final JTree tree1 = createTree(true);
    PopupHandler.installPopupHandler(tree1, group, ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    baseOnThisTypeAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(), tree1);
    trees.put(TYPE_HIERARCHY_TYPE, tree1);

    final JTree tree2 = createTree(true);
    PopupHandler.installPopupHandler(tree2, group, ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    baseOnThisTypeAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(), tree2);
    trees.put(SUPERTYPES_HIERARCHY_TYPE, tree2);

    final JTree tree3 = createTree(true);
    PopupHandler.installPopupHandler(tree3, group, ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    baseOnThisTypeAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(), tree3);
    trees.put(SUBTYPES_HIERARCHY_TYPE, tree3);
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

  protected boolean isInterface(PsiElement psiElement) {
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
