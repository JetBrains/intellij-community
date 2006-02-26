/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.tree.actions;

import com.intellij.javaee.model.ElementPresentation;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.util.Icons;
import com.intellij.util.xml.tree.BaseDomElementNode;
import com.intellij.util.xml.tree.DomModelTreeView;
import jetbrains.fabrique.ui.treeStructure.SimpleNode;

/**
 * User: Sergey.Vasiliev
 * Date: Feb 26, 2006
 */
public class DeleteElementFromCollection extends AnAction {
  private DomModelTreeView myTreeView;


  public DeleteElementFromCollection(final DomModelTreeView treeView) {
    myTreeView = treeView;
  }

  public void actionPerformed(AnActionEvent e) {
     if (myTreeView.getTree().getSelectedNode() instanceof BaseDomElementNode) {
       final BaseDomElementNode selectedNode = (BaseDomElementNode)myTreeView.getTree().getSelectedNode();
       new WriteCommandAction(selectedNode.getDomElement().getParent().getManager().getProject()) {
         protected void run(final Result result) throws Throwable {

           selectedNode.getDomElement().undefine();
         }
       }.execute();
     }
  }

  public void update(AnActionEvent e) {
    final SimpleNode selectedNode = myTreeView.getTree().getSelectedNode();

    final boolean enabled = selectedNode instanceof BaseDomElementNode;
    e.getPresentation().setEnabled(enabled);
    if (enabled) {
      final ElementPresentation presentation = ((BaseDomElementNode)selectedNode).getDomElement().getPresentation();
      e.getPresentation().setText("Delete " + presentation.getTypeName()+(presentation.getElementName() == null? "": ": " + presentation.getElementName()));
    } else {
      e.getPresentation().setText("");
    }

    e.getPresentation().setIcon(Icons.DELETE_ICON);
  }
}
