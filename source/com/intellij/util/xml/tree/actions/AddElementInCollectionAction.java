/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.tree.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.application.Result;
import com.intellij.util.xml.tree.DomModelTreeView;
import com.intellij.util.xml.tree.DomElementsGroupNode;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.Icons;
import com.intellij.javaee.model.ElementPresentationManager;
import com.sun.corba.se.impl.presentation.rmi.PresentationManagerImpl;
import jetbrains.fabrique.ui.treeStructure.SimpleNode;

import java.lang.reflect.Type;

/**
 * User: Sergey.Vasiliev
 */
public class AddElementInCollectionAction extends AnAction {
  private DomModelTreeView myTreeView;


  public AddElementInCollectionAction(final DomModelTreeView treeView) {
    myTreeView = treeView;
  }

  public void actionPerformed(AnActionEvent e) {
     if (myTreeView.getTree().getSelectedNode() instanceof DomElementsGroupNode) {
       final DomElementsGroupNode selectedNode = (DomElementsGroupNode)myTreeView.getTree().getSelectedNode();
       new WriteCommandAction(selectedNode.getDomElement().getParent().getManager().getProject()) {
         protected void run(final Result result) throws Throwable {
             selectedNode.getChildDescription().addValue(selectedNode.getDomElement());
         }
       }.execute();
     }
  }

  public void update(AnActionEvent e) {
    final SimpleNode selectedNode = myTreeView.getTree().getSelectedNode();
    final boolean enabled = selectedNode instanceof DomElementsGroupNode;

    e.getPresentation().setEnabled(enabled);

    if(enabled) {
      final Type type = ((DomElementsGroupNode)selectedNode).getChildDescription().getType();
      e.getPresentation().setText("Add " + ElementPresentationManager.getPresentationForClass(DomUtil.getRawType(type)).getElementName());
    } else {
      e.getPresentation().setText("");
    }

    e.getPresentation().setIcon(Icons.ADD_ICON);
  }
}
