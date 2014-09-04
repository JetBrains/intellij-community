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
package com.jetbrains.python.hierarchy.call;

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.ui.PopupHandler;
import com.jetbrains.python.hierarchy.PyHierarchyUtils;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;
import java.util.Map;

/**
 * @author novokrest
 */
public class PyCallHierarchyBrowser extends CallHierarchyBrowserBase {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.hierarchy.call.PyCallHierarchyBrowser");
  private static final String GROUP_PY_CALL_HIERARCHY_POPUP = "PyCallHierarchyPopupMenu";

  public PyCallHierarchyBrowser(PsiElement function) {
    super(function.getProject(), function);
  }

  @Nullable
  @Override
  protected PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
    if (descriptor instanceof PyCallHierarchyNodeDescriptor) {
      PyCallHierarchyNodeDescriptor nodeDescriptor = (PyCallHierarchyNodeDescriptor)descriptor;
      return nodeDescriptor.getEnclosingElement();
    }
    return null;
  }

  @Override
  protected PsiElement getOpenFileElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
    if (descriptor instanceof PyCallHierarchyNodeDescriptor) {
      PyCallHierarchyNodeDescriptor nodeDescriptor = (PyCallHierarchyNodeDescriptor)descriptor;
      return nodeDescriptor.getTargetElement();
    }
    return null;
  }

  @Override
  protected void createTrees(@NotNull Map<String, JTree> type2TreeMap) {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(GROUP_PY_CALL_HIERARCHY_POPUP);
    final JTree tree1 = createTree(false);
    PopupHandler.installPopupHandler(tree1, group, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    final BaseOnThisFunctionAction baseOnThisFunctionAction = new BaseOnThisFunctionAction();
    baseOnThisFunctionAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).getShortcutSet(), tree1);
    type2TreeMap.put(CALLER_TYPE, tree1);

    final JTree tree2 = createTree(false);
    PopupHandler.installPopupHandler(tree2, group, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    baseOnThisFunctionAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).getShortcutSet(), tree2);
    type2TreeMap.put(CALLEE_TYPE, tree2);
  }

  @Override
  protected boolean isApplicableElement(@NotNull PsiElement element) {
    return element instanceof PyFunction || element instanceof PyClass || element instanceof PyFile;
  }

  @Nullable
  @Override
  protected HierarchyTreeStructure createHierarchyTreeStructure(@NotNull String typeName, @NotNull PsiElement psiElement) {
    if (CALLER_TYPE.equals(typeName)) {
      return new PyCallerFunctionTreeStructure(myProject, psiElement, getCurrentScopeType());
    }
    else if (CALLEE_TYPE.equals(typeName)) {
      return new PyCalleeFunctionTreeStructure(myProject, psiElement, getCurrentScopeType());
    }
    else {
      LOG.error("unexpected type: " + typeName);
      return null;
    }
  }

  @Nullable
  @Override
  protected Comparator<NodeDescriptor> getComparator() {
    return PyHierarchyUtils.getComparator(myProject);
  }

  public static final class BaseOnThisFunctionAction extends CallHierarchyBrowserBase.BaseOnThisMethodAction {
  }
}
