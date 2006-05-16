/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.tree.actions;

import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ui.actions.DefaultAddAction;
import com.intellij.util.xml.ui.actions.AddDomElementAction;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.tree.DomElementsGroupNode;
import com.intellij.util.xml.tree.DomModelTreeView;
import com.intellij.util.xml.tree.BaseDomElementNode;
import com.intellij.ui.treeStructure.SimpleNode;

import javax.swing.*;
import java.lang.reflect.Type;

/**
 * User: Sergey.Vasiliev
 */
public class AddElementInCollectionAction extends AddDomElementAction {
  private DomModelTreeView myTreeView;

  public AddElementInCollectionAction() {
  }

  public AddElementInCollectionAction(final DomModelTreeView treeView) {
    myTreeView = treeView;
  }

  protected DomModelTreeView getTreeView(AnActionEvent e) {
    if (myTreeView != null) return myTreeView;

    return (DomModelTreeView)e.getDataContext().getData(DomModelTreeView.DOM_MODEL_TREE_VIEW_KEY);
  }

  protected boolean isEnabled(final AnActionEvent e) {
    final DomModelTreeView treeView = getTreeView(e);

    final boolean enabled = treeView != null && getDomElementsGroupNode(treeView) != null;
    e.getPresentation().setEnabled(enabled);

    return enabled;
  }


  protected void showPopup(final ListPopup groupPopup, final AnActionEvent e) {
    if(myTreeView == null) {
      if(e.getPlace().equals(DomModelTreeView.DOM_MODEL_TREE_VIEW_POPUP)) {
          groupPopup.showInCenterOf(getTreeView(e).getTree());
      } else {
          groupPopup.showInBestPositionFor(e.getDataContext());
      }
    } else {
        super.showPopup(groupPopup, e);
    }
  }

  protected DomCollectionChildDescription[] getDomCollectionChildDescriptions(final AnActionEvent e) {
    final DomModelTreeView view = getTreeView(e);

    SimpleNode node = view.getTree().getSelectedNode();
    if (node instanceof BaseDomElementNode) {
      return ((BaseDomElementNode)node).getConsolidatedChildrenDescriptions().toArray(DomCollectionChildDescription.EMPTY_ARRAY);
    }

    final DomElementsGroupNode groupNode = getDomElementsGroupNode(view);

    return new DomCollectionChildDescription[] { groupNode.getChildDescription()};
  }

  protected DomElement getParentDomElement(final AnActionEvent e) {
    final DomModelTreeView view = getTreeView(e);
    SimpleNode node = view.getTree().getSelectedNode();
    if (node instanceof BaseDomElementNode) {
      if (((BaseDomElementNode)node).getConsolidatedChildrenDescriptions().size() > 0) {
        return ((BaseDomElementNode)node).getDomElement(); 
      }
    }
    final DomElementsGroupNode groupNode = getDomElementsGroupNode(view);

    return groupNode.getDomElement();
  }

 protected String getActionText(final AnActionEvent e) {
   String text = "Add";
   if (e.getPresentation().isEnabled()) {
      final DomElementsGroupNode selectedNode = getDomElementsGroupNode(getTreeView(e));
      final Type type = selectedNode.getChildDescription().getType();
      text += " " + ElementPresentationManager.getTypeName(DomUtil.getRawType(type));
    }
    return text;
  }

  private DomElementsGroupNode getDomElementsGroupNode(final DomModelTreeView treeView) {
    SimpleNode simpleNode = treeView.getTree().getSelectedNode();
    while (simpleNode != null) {
      if (simpleNode instanceof DomElementsGroupNode) return (DomElementsGroupNode)simpleNode;

      simpleNode = simpleNode.getParent();
    }
    return null;
  }


  protected DefaultAddAction createAddingAction(final AnActionEvent e,
                                                final String name,
                                                final Icon icon,
                                                final Class s,
                                                final DomCollectionChildDescription description) {

    return new DefaultAddAction(name, "", icon) {
      // we need this properties, don't remove it (shared dataContext assertion)
      private DomElement  myParent = AddElementInCollectionAction.this.getParentDomElement(e);
      private DomModelTreeView myView = getTreeView(e);

      protected Class getElementClass() {
        return s;
      }

      protected DomCollectionChildDescription getDomCollectionChildDescription() {
        return description;
      }

      protected DomElement getParentDomElement() {
        return myParent;
      }

      protected void afterAddition(final AnActionEvent e, final DomElement newElement) {
        myView.setSelectedDomElement(newElement);
      }
    };
  }
}
