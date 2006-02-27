/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.xml.tree.DomModelTreeView;
import com.intellij.util.xml.tree.BaseDomElementNode;
import jetbrains.fabrique.ui.treeStructure.SimpleNode;

/**
 * User: Sergey.Vasiliev
 */
public class GotoDomElementDeclarationAction extends BaseDomTreeAction {

  public void actionPerformed(AnActionEvent e, DomModelTreeView treeView) {
    final SimpleNode simpleNode = treeView.getTree().getSelectedNode();
    
    if(simpleNode instanceof BaseDomElementNode) {
         ((BaseDomElementNode)simpleNode).getDomElement().navigate(true);
    }
  }

  public void update(AnActionEvent e, DomModelTreeView treeView) {
     e.getPresentation().setText("Go to declaration");
  }
}
