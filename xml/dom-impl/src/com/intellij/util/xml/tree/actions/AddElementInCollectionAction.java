// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.xml.tree.actions;

import com.intellij.ide.TypePresentationService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsActions;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.MergedObject;
import com.intellij.util.xml.XmlDomBundle;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.tree.BaseDomElementNode;
import com.intellij.util.xml.tree.DomElementsGroupNode;
import com.intellij.util.xml.tree.DomModelTreeView;
import com.intellij.util.xml.ui.actions.AddDomElementAction;
import com.intellij.util.xml.ui.actions.DefaultAddAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Type;
import java.util.List;

public class AddElementInCollectionAction extends AddDomElementAction {
  private DomModelTreeView myTreeView;

  public AddElementInCollectionAction() {
  }

  public AddElementInCollectionAction(final DomModelTreeView treeView) {
    myTreeView = treeView;
  }

  protected DomModelTreeView getTreeView(AnActionEvent e) {
    if (myTreeView != null) return myTreeView;

    return e.getData(DomModelTreeView.DATA_KEY);
  }

  @Override
  protected boolean isEnabled(final AnActionEvent e) {
    final DomModelTreeView treeView = getTreeView(e);

    final boolean enabled = treeView != null;
    e.getPresentation().setEnabled(enabled);

    return enabled;
  }


  @Override
  protected void showPopup(final ListPopup groupPopup, final AnActionEvent e) {
    if (myTreeView == null) {
      if (e.getPlace().equals(DomModelTreeView.DOM_MODEL_TREE_VIEW_POPUP)) {
        groupPopup.showInCenterOf(getTreeView(e).getTree());
      }
      else {
        groupPopup.showInBestPositionFor(e.getDataContext());
      }
    }
    else {
      super.showPopup(groupPopup, e);
    }
  }

  @Override
  protected DomCollectionChildDescription @NotNull [] getDomCollectionChildDescriptions(final AnActionEvent e) {
    final DomModelTreeView view = getTreeView(e);

    SimpleNode node = view.getTree().getSelectedNode();
    if (node instanceof BaseDomElementNode) {
      List<DomCollectionChildDescription> consolidated = ((BaseDomElementNode)node).getConsolidatedChildrenDescriptions();
      if (consolidated.size() > 0) {
        return consolidated.toArray(DomCollectionChildDescription.EMPTY_ARRAY);
      }
    }

    final DomElementsGroupNode groupNode = getDomElementsGroupNode(view);

    return groupNode == null
           ? DomCollectionChildDescription.EMPTY_ARRAY
           : new DomCollectionChildDescription[]{groupNode.getChildDescription()};
  }

  @Override
  protected DomElement getParentDomElement(final AnActionEvent e) {
    final DomModelTreeView view = getTreeView(e);
    SimpleNode node = view.getTree().getSelectedNode();
    if (node instanceof BaseDomElementNode) {
      if (((BaseDomElementNode)node).getConsolidatedChildrenDescriptions().size() > 0) {
        return ((BaseDomElementNode)node).getDomElement();
      }
    }
    final DomElementsGroupNode groupNode = getDomElementsGroupNode(view);

    return groupNode == null ? null : groupNode.getDomElement();
  }

  @Override
  protected JComponent getComponent(AnActionEvent e) {
    return getTreeView(e);
  }

  @Override
  protected String getActionText(final AnActionEvent e) {
    String text = XmlDomBundle.message("dom.action.add");
    if (e.getPresentation().isEnabled()) {
      final DomElementsGroupNode selectedNode = getDomElementsGroupNode(getTreeView(e));
      if (selectedNode != null) {
        final Type type = selectedNode.getChildDescription().getType();

        text += " " + TypePresentationService.getService().getTypePresentableName(ReflectionUtil.getRawType(type));
      }
    }
    return text;
  }

  @Nullable
  private static DomElementsGroupNode getDomElementsGroupNode(final DomModelTreeView treeView) {
    SimpleNode simpleNode = treeView.getTree().getSelectedNode();
    while (simpleNode != null) {
      if (simpleNode instanceof DomElementsGroupNode) return (DomElementsGroupNode)simpleNode;

      simpleNode = simpleNode.getParent();
    }
    return null;
  }


  @Override
  protected AnAction createAddingAction(final AnActionEvent e,
                                                final @NlsActions.ActionText String name,
                                                final Icon icon,
                                                final Type type,
                                                final DomCollectionChildDescription description) {

    final DomElement parentDomElement = getParentDomElement(e);

    if (parentDomElement instanceof MergedObject) {
      @SuppressWarnings("unchecked") final List<DomElement> implementations =
        ((MergedObject<DomElement>)parentDomElement).getImplementations();
      final DefaultActionGroup actionGroup = DefaultActionGroup.createPopupGroup(() -> name);

      for (DomElement implementation : implementations) {
        final XmlFile xmlFile = DomUtil.getFile(implementation);
        actionGroup.add(new MyDefaultAddAction(implementation, xmlFile.getName(), xmlFile.getIcon(0), e, type, description));
      }
      return actionGroup;
    }

    return new MyDefaultAddAction(parentDomElement, name, icon, e, type, description);
  }

  private class MyDefaultAddAction extends DefaultAddAction {
    // we need this properties, don't remove it (shared dataContext assertion)
    private final DomElement myParent;
    private final DomModelTreeView myView;
    private final Type myType;
    private final DomCollectionChildDescription myDescription;

    MyDefaultAddAction(final DomElement parent,
                              final @NlsActions.ActionText String name,
                              final Icon icon,
                              final AnActionEvent e,
                              final Type type,
                              final DomCollectionChildDescription description) {
      super(name, name, icon);
      myType = type;
      myDescription = description;
      myParent = parent;
      myView = getTreeView(e);
    }

    @Override
    protected Type getElementType() {
      return myType;
    }

    @Override
    protected DomCollectionChildDescription getDomCollectionChildDescription() {
      return myDescription;
    }

    @Override
    protected DomElement getParentDomElement() {
      return myParent;
    }

    @Override
    protected void afterAddition(@NotNull final DomElement newElement) {
      final DomElement copy = newElement.createStableCopy();

      ApplicationManager.getApplication().invokeLater(() -> myView.setSelectedDomElement(copy));

    }
  }
}
