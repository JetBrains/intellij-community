package com.intellij.ide.hierarchy.type;

import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.ui.PopupHandler;

import javax.swing.*;
import java.util.Comparator;
import java.util.Hashtable;

public final class TypeHierarchyBrowser extends TypeHierarchyBrowserBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.hierarchy.type.TypeHierarchyBrowser");


  public TypeHierarchyBrowser(final Project project, final PsiClass psiClass) {
    super(project, psiClass);
  }

  protected boolean isInterface(PsiElement psiElement) {
    return psiElement instanceof PsiClass && ((PsiClass)psiElement).isInterface();
  }

  protected void createTrees(final Hashtable<String, JTree> trees) {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_TYPE_HIERARCHY_POPUP);
    final BaseOnThisTypeAction baseOnThisTypeAction = new BaseOnThisTypeAction();
    final JTree tree1 = createTreeWithoutActions();
    PopupHandler.installPopupHandler(tree1, group, ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    baseOnThisTypeAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(), tree1);
    trees.put(TYPE_HIERARCHY_TYPE, tree1);

    final JTree tree2 = createTreeWithoutActions();
    PopupHandler.installPopupHandler(tree2, group, ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    baseOnThisTypeAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(), tree2);
    trees.put(SUPERTYPES_HIERARCHY_TYPE, tree2);

    final JTree tree3 = createTreeWithoutActions();
    PopupHandler.installPopupHandler(tree3, group, ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    baseOnThisTypeAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(), tree3);
    trees.put(SUBTYPES_HIERARCHY_TYPE, tree3);
  }

  protected PsiClass getPsiElementFromNodeDescriptor(final Object userObject) {
    if (!(userObject instanceof TypeHierarchyNodeDescriptor)) return null;
    return ((TypeHierarchyNodeDescriptor)userObject).getPsiClass();
  }

  protected String getNameForClass(final PsiElement element) {
    return ClassPresentationUtil.getNameForClass((PsiClass)element, false);
  }

  protected boolean isApplicableElement(final PsiElement element) {
    return element instanceof PsiClass;
  }

  protected Comparator<NodeDescriptor> getComparator() {
    return JavaHierarchyUtil.getComparator(myProject);
  }

  protected HierarchyTreeStructure createHierarchyTreeStructure(final String typeName, final PsiElement psiElement) {
    if (SUPERTYPES_HIERARCHY_TYPE.equals(typeName)) {
      return new SupertypesHierarchyTreeStructure(myProject, (PsiClass)psiElement);
    }
    else if (SUBTYPES_HIERARCHY_TYPE.equals(typeName)) {
      return new SubtypesHierarchyTreeStructure(myProject, (PsiClass)psiElement);
    }
    else if (TYPE_HIERARCHY_TYPE.equals(typeName)) {
      return new TypeHierarchyTreeStructure(myProject, (PsiClass)psiElement);
    }
    else {
      LOG.error("unexpected type: " + typeName);
      return null;
    }
  }

  protected boolean canBeDeleted(final PsiElement psiElement) {
    return psiElement instanceof PsiClass && !(psiElement instanceof PsiAnonymousClass);
  }

  protected String getQualifiedName(final PsiElement psiElement) {
    if (psiElement instanceof PsiClass) {
      return ((PsiClass)psiElement).getQualifiedName();
    }
    return "";
  }

  public static class BaseOnThisTypeAction extends TypeHierarchyBrowserBase.BaseOnThisTypeAction {
    protected boolean isEnabled(final TypeHierarchyBrowserBase browser, final PsiElement psiElement) {
      return super.isEnabled(browser, psiElement) && !"java.lang.Object".equals(((PsiClass)psiElement).getQualifiedName());
    }
  }
}
