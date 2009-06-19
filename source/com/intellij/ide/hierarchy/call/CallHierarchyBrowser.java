package com.intellij.ide.hierarchy.call;

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.PopupHandler;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

public final class CallHierarchyBrowser extends CallHierarchyBrowserBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.hierarchy.call.CallHierarchyBrowser");

  public CallHierarchyBrowser(final Project project, final PsiMethod method) {
    super(project, method);
  }

  protected void createTrees(final Map<String, JTree> type2TreeMap) {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_CALL_HIERARCHY_POPUP);
    final JTree tree1 = createTreeWithoutActions();
    PopupHandler.installPopupHandler(tree1, group, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    final BaseOnThisMethodAction baseOnThisMethodAction = new BaseOnThisMethodAction();
    baseOnThisMethodAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).getShortcutSet(), tree1);
    type2TreeMap.put(CALLEE_TYPE, tree1);

    final JTree tree2 = createTreeWithoutActions();
    PopupHandler.installPopupHandler(tree2, group, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    baseOnThisMethodAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).getShortcutSet(), tree2);
    type2TreeMap.put(CALLER_TYPE, tree2);
  }

  protected PsiElement getTargetElementFromNode(final DefaultMutableTreeNode node) {
    final Object userObject = node.getUserObject();
    if (userObject instanceof CallHierarchyNodeDescriptor) {
      CallHierarchyNodeDescriptor nodeDescriptor = (CallHierarchyNodeDescriptor)userObject;
      return nodeDescriptor.getTargetElement();
    }
    return null;
  }

  protected PsiElement getEnclosingElementFromNode(final DefaultMutableTreeNode node) {
    if (node == null) return null;
    final Object userObject = node.getUserObject();
    if (!(userObject instanceof CallHierarchyNodeDescriptor)) return null;
    return ((CallHierarchyNodeDescriptor)userObject).getEnclosingElement();
  }

  protected boolean isApplicableElement(final PsiElement element) {
    return element instanceof PsiMethod;
  }

  protected HierarchyTreeStructure createHierarchyTreeStructure(final String typeName, final PsiElement psiElement) {
    if (CALLER_TYPE.equals(typeName)) {
      return new CallerMethodsTreeStructure(myProject, (PsiMethod)psiElement, getCurrentScopeType());
    }
    else if (CALLEE_TYPE.equals(typeName)) {
      return new CalleeMethodsTreeStructure(myProject, (PsiMethod)psiElement, getCurrentScopeType());
    }
    else {
      LOG.error("unexpected type: " + typeName);
      return null;
    }
  }

  protected Comparator<NodeDescriptor> getComparator() {
    return JavaHierarchyUtil.getComparator(myProject);
  }

  protected PsiMethod[] getSelectedMethods() {
    JTree tree = getCurrentTree();
    if (tree == null) return PsiMethod.EMPTY_ARRAY;
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null) return PsiMethod.EMPTY_ARRAY;
    ArrayList<PsiMethod> psiMethods = new ArrayList<PsiMethod>();
    for (TreePath path : paths) {
      Object node = path.getLastPathComponent();
      if (!(node instanceof DefaultMutableTreeNode)) continue;
      Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
      if (!(userObject instanceof CallHierarchyNodeDescriptor)) continue;
      PsiMember enclosingElement = ((CallHierarchyNodeDescriptor)userObject).getEnclosingElement();
      if (!(enclosingElement instanceof PsiMethod)) continue;
      psiMethods.add((PsiMethod)enclosingElement);
    }
    return psiMethods.toArray(new PsiMethod[psiMethods.size()]);
  }

  public static final class BaseOnThisMethodAction extends CallHierarchyBrowserBase.BaseOnThisMethodAction{}
}
