package com.intellij.ide.hierarchy.method;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.*;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Hashtable;

public final class MethodHierarchyBrowser extends MethodHierarchyBrowserBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.hierarchy.method.MethodHierarchyBrowser");

  public MethodHierarchyBrowser(final Project project, final PsiMethod method) {
    super(project, method);
  }

  protected void createTrees(Hashtable<String, JTree> trees) {
    final JTree tree = createTreeWithoutActions();
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_METHOD_HIERARCHY_POPUP);
    PopupHandler.installPopupHandler(tree, group, ActionPlaces.METHOD_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());

    final BaseOnThisMethodAction baseOnThisMethodAction = new BaseOnThisMethodAction();
    baseOnThisMethodAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_METHOD_HIERARCHY).getShortcutSet(), tree);

    trees.put(MethodHierarchyTreeStructure.TYPE, tree);
  }

  protected JPanel createLegendPanel() {
    return createStandardLegendPanel(IdeBundle.message("hierarchy.legend.method.is.defined.in.class"),
                                     IdeBundle.message("hierarchy.legend.method.defined.in.superclass"),
                                     IdeBundle.message("hierarchy.legend.method.should.be.defined"));
  }

  protected PsiElement getTargetElementFromNode(final DefaultMutableTreeNode node) {
    final Object userObject = node == null ? null : node.getUserObject();
    if (userObject instanceof MethodHierarchyNodeDescriptor) {
      MethodHierarchyNodeDescriptor nodeDescriptor = (MethodHierarchyNodeDescriptor)userObject;
      return nodeDescriptor.getTargetElement();
    }
    return null;
  }

  protected PsiElement getElementFromDescriptor(final HierarchyNodeDescriptor descriptor) {
    if (descriptor instanceof MethodHierarchyNodeDescriptor) {
      return ((MethodHierarchyNodeDescriptor)descriptor).getTargetElement();
    }
    return null;
  }

  protected boolean isApplicableElement(final PsiElement psiElement) {
    return psiElement instanceof PsiMethod;
  }

  protected PsiMethod[] getSelectedMethods() {
    HierarchyNodeDescriptor[] descriptors = getSelectedDescriptors();
    ArrayList<PsiMethod> psiElements = new ArrayList<PsiMethod>();
    for (HierarchyNodeDescriptor descriptor : descriptors) {
      if (descriptor instanceof MethodHierarchyNodeDescriptor) {
        final PsiElement element = ((MethodHierarchyNodeDescriptor)descriptor).getTargetElement();
        if (!(element instanceof PsiMethod)) continue;
        psiElements.add((PsiMethod)element);
      }
    }
    return psiElements.toArray(new PsiMethod[psiElements.size()]);
  }

  protected HierarchyTreeStructure createHierarchyTreeStructure(final String typeName, final PsiElement psiElement) {
    if (!MethodHierarchyTreeStructure.TYPE.equals(typeName)) {
      LOG.error("unexpected type: " + typeName);
      return null;
    }
    final HierarchyTreeStructure structure = new MethodHierarchyTreeStructure(myProject, (PsiMethod)psiElement);
    return structure;
  }

  protected Comparator<NodeDescriptor> getComparator() {
    return JavaHierarchyUtil.getComparator(myProject);
  }

  public static final class BaseOnThisMethodAction extends MethodHierarchyBrowserBase.BaseOnThisMethodAction {
  }

  @Nullable
  public final PsiMethod getBaseMethod() {
    final HierarchyTreeBuilder builder = myBuilders.get(myCurrentViewName);
    final MethodHierarchyTreeStructure treeStructure = (MethodHierarchyTreeStructure)builder.getTreeStructure();

    final PsiMethod baseMethod = treeStructure.getBaseMethod();
    return baseMethod;
  }
}
