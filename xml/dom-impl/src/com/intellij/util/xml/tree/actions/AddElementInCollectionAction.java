/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.xml.tree.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.MergedObject;
import com.intellij.util.xml.TypeNameManager;
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

    return DomModelTreeView.DATA_KEY.getData(e.getDataContext());
  }

  protected boolean isEnabled(final AnActionEvent e) {
    final DomModelTreeView treeView = getTreeView(e);

    final boolean enabled = treeView != null;
    e.getPresentation().setEnabled(enabled);

    return enabled;
  }


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

  @NotNull
  protected DomCollectionChildDescription[] getDomCollectionChildDescriptions(final AnActionEvent e) {
    final DomModelTreeView view = getTreeView(e);

    SimpleNode node = view.getTree().getSelectedNode();
    if (node instanceof BaseDomElementNode) {
      List<DomCollectionChildDescription> consolidated = ((BaseDomElementNode)node).getConsolidatedChildrenDescriptions();
      if (consolidated.size() > 0) {
        return consolidated.toArray(new DomCollectionChildDescription[consolidated.size()]);
      }
    }

    final DomElementsGroupNode groupNode = getDomElementsGroupNode(view);

    return groupNode == null
           ? DomCollectionChildDescription.EMPTY_ARRAY
           : new DomCollectionChildDescription[]{groupNode.getChildDescription()};
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

    return groupNode == null ? null : groupNode.getDomElement();
  }

  protected JComponent getComponent(AnActionEvent e) {
    return getTreeView(e);
  }

  protected boolean showAsPopup() {
    return true;
  }

  protected String getActionText(final AnActionEvent e) {
    String text = ApplicationBundle.message("action.add");
    if (e.getPresentation().isEnabled()) {
      final DomElementsGroupNode selectedNode = getDomElementsGroupNode(getTreeView(e));
      if (selectedNode != null) {
        final Type type = selectedNode.getChildDescription().getType();
        text += " " + TypeNameManager.getTypeName(ReflectionUtil.getRawType(type));
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


  protected AnAction createAddingAction(final AnActionEvent e,
                                                final String name,
                                                final Icon icon,
                                                final Type type,
                                                final DomCollectionChildDescription description) {

    final DomElement parentDomElement = getParentDomElement(e);

    if (parentDomElement instanceof MergedObject) {
      final List<DomElement> implementations = (List<DomElement>)((MergedObject)parentDomElement).getImplementations();
      final DefaultActionGroup actionGroup = new DefaultActionGroup(name, true);

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

    public MyDefaultAddAction(final DomElement parent,
                              final String name,
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

    protected Type getElementType() {
      return myType;
    }

    protected DomCollectionChildDescription getDomCollectionChildDescription() {
      return myDescription;
    }

    protected DomElement getParentDomElement() {
      return myParent;
    }

    protected void afterAddition(final DomElement newElement) {
      final DomElement copy = newElement.createStableCopy();

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myView.setSelectedDomElement(copy);
        }
      });

    }
  }
}
