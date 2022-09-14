// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.hierarchy.call;

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.ui.PopupHandler;
import com.jetbrains.python.hierarchy.PyHierarchyNodeDescriptor;
import com.jetbrains.python.hierarchy.PyHierarchyUtils;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;
import java.util.Map;

/**
 * @author novokrest
 */
public class PyCallHierarchyBrowser extends CallHierarchyBrowserBase {
  private static final Logger LOG = Logger.getInstance(PyCallHierarchyBrowser.class);

  public PyCallHierarchyBrowser(PsiElement function) {
    super(function.getProject(), function);
  }

  @Nullable
  @Override
  protected PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
    if (descriptor instanceof PyHierarchyNodeDescriptor) {
      PyHierarchyNodeDescriptor pyDescriptor = (PyHierarchyNodeDescriptor)descriptor;
      return pyDescriptor.getPsiElement();
    }
    return null;
  }

  @Override
  protected void createTrees(@NotNull Map<? super @Nls String, ? super JTree> type2TreeMap) {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_CALL_HIERARCHY_POPUP);

    JTree callerTree = createHierarchyTree(group);
    JTree calleeTree = createHierarchyTree(group);

    type2TreeMap.put(getCallerType(), callerTree);
    type2TreeMap.put(getCalleeType(), calleeTree);
  }

  private JTree createHierarchyTree(ActionGroup group) {
    JTree tree = createTree(false);
    PopupHandler.installPopupMenu(tree, group, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP);
    return tree;
  }

  @Override
  protected boolean isApplicableElement(@NotNull PsiElement element) {
    return element instanceof PyFunction || element instanceof PyClass || element instanceof PyFile;
  }

  @Nullable
  @Override
  protected HierarchyTreeStructure createHierarchyTreeStructure(@NotNull String typeName, @NotNull PsiElement psiElement) {
    if (getCallerType().equals(typeName)) {
      return new PyCallerFunctionTreeStructure(myProject, psiElement, getCurrentScopeType());
    }
    else if (getCalleeType().equals(typeName)) {
      return new PyCalleeFunctionTreeStructure(myProject, psiElement, getCurrentScopeType());
    }
    else {
      LOG.error("unexpected type: " + typeName);
      return null;
    }
  }

  @Nullable
  @Override
  protected Comparator<NodeDescriptor<?>> getComparator() {
    return PyHierarchyUtils.getComparator(myProject);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
