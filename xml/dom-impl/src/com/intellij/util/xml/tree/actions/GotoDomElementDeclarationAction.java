/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.util.xml.tree.DomModelTreeView;
import com.intellij.util.xml.tree.BaseDomElementNode;
import com.intellij.util.xml.DomElementsNavigationManager;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementNavigationProvider;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.idea.ActionsBundle;

/**
 * User: Sergey.Vasiliev
 */
public class GotoDomElementDeclarationAction extends BaseDomTreeAction {

  public void actionPerformed(AnActionEvent e, DomModelTreeView treeView) {
    final SimpleNode simpleNode = treeView.getTree().getSelectedNode();

    if(simpleNode instanceof BaseDomElementNode) {
      final DomElement domElement = ((BaseDomElementNode)simpleNode).getDomElement();
      final DomElementNavigationProvider provider =
        DomElementsNavigationManager.getManager(domElement.getManager().getProject()).getDomElementsNavigateProvider(DomElementsNavigationManager.DEFAULT_PROVIDER_NAME);

      provider.navigate(domElement, true);

    }
  }

  public void update(AnActionEvent e, DomModelTreeView treeView) {
    e.getPresentation().setVisible(treeView.getTree().getSelectedNode() instanceof BaseDomElementNode);
    e.getPresentation().setText(ActionsBundle.message("action.EditSource.text"));
  }
}
