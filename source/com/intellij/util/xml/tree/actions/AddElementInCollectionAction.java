/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.tree.actions;

import com.intellij.javaee.model.ElementPresentationManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.util.Icons;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.tree.DomElementsGroupNode;
import com.intellij.util.xml.tree.DomModelTreeView;
import jetbrains.fabrique.ui.treeStructure.SimpleNode;

import java.lang.reflect.Type;

/**
 * User: Sergey.Vasiliev
 */
public class AddElementInCollectionAction extends BaseDomTreeAction {


  public AddElementInCollectionAction() {
  }

  public AddElementInCollectionAction(final DomModelTreeView treeView) {
    super(treeView);
  }


  public void actionPerformed(AnActionEvent e, DomModelTreeView treeView) {
     if (treeView.getTree().getSelectedNode() instanceof DomElementsGroupNode) {
       final DomElementsGroupNode selectedNode = (DomElementsGroupNode)treeView.getTree().getSelectedNode();
       new WriteCommandAction(selectedNode.getDomElement().getParent().getManager().getProject()) {
         protected void run(final Result result) throws Throwable {
             selectedNode.getChildDescription().addValue(selectedNode.getDomElement());
         }
       }.execute();
     }
  }

  public void update(AnActionEvent e, DomModelTreeView treeView) {
    final SimpleNode selectedNode = treeView.getTree().getSelectedNode();
    final boolean enabled = selectedNode instanceof DomElementsGroupNode;

    e.getPresentation().setEnabled(enabled);
    //e.getPresentation().setVisible(enabled);

    if(enabled) {
      final Type type = ((DomElementsGroupNode)selectedNode).getChildDescription().getType();
      e.getPresentation().setText("Add " + ElementPresentationManager.getPresentationForClass(DomUtil.getRawType(type)).getElementName());
    } else {
      e.getPresentation().setText("Add element");
    }

    e.getPresentation().setIcon(Icons.ADD_ICON);
  }
}
