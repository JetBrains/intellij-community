/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.util.xml.tree;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.ui.treeStructure.WeightBasedComparator;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;

public class DomModelTreeView extends Wrapper implements DataProvider, Disposable {
  public static final DataKey<DomModelTreeView> DATA_KEY = DataKey.create("DOM_MODEL_TREE_VIEW_KEY");
  @NonNls public static String DOM_MODEL_TREE_VIEW_POPUP = "DOM_MODEL_TREE_VIEW_POPUP";

  private final SimpleTree myTree;
  private final AbstractTreeBuilder myBuilder;
  private final DomManager myDomManager;
  @Nullable private final DomElement myRootElement;

  public DomModelTreeView(@NotNull DomElement rootElement) {
    this(rootElement, rootElement.getManager(), new DomModelTreeStructure(rootElement));
  }

  protected DomModelTreeView(DomElement rootElement, DomManager manager, SimpleTreeStructure treeStructure) {
    myDomManager = manager;
    myRootElement = rootElement;
    myTree = new SimpleTree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myTree.setRootVisible(isRootVisible());
    myTree.setShowsRootHandles(true);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    ToolTipManager.sharedInstance().registerComponent(myTree);
    TreeUtil.installActions(myTree);

    myBuilder = new AbstractTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), treeStructure, WeightBasedComparator.INSTANCE, false);
    Disposer.register(this, myBuilder);

    myBuilder.setNodeDescriptorComparator(null);

    myBuilder.initRootNode();

    add(myTree, BorderLayout.CENTER);

    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        final SimpleNode simpleNode = myTree.getNodeFor(event.getPath());

        if (simpleNode instanceof AbstractDomElementNode) {
          ((AbstractDomElementNode)simpleNode).setExpanded(true);
        }
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        final SimpleNode simpleNode = myTree.getNodeFor(event.getPath());

        if (simpleNode instanceof AbstractDomElementNode) {
          ((AbstractDomElementNode)simpleNode).setExpanded(false);
          simpleNode.update();
        }
      }
    });

    myDomManager.addDomEventListener(new DomChangeAdapter() {
      @Override
      protected void elementChanged(DomElement element) {
        if (element.isValid()) {
          queueUpdate(DomUtil.getFile(element).getVirtualFile());
        } else if (element instanceof DomFileElement) {
          final XmlFile xmlFile = ((DomFileElement)element).getFile();
          queueUpdate(xmlFile.getVirtualFile());
        }
      }
    }, this);


    final Project project = myDomManager.getProject();
    DomElementAnnotationsManager.getInstance(project).addHighlightingListener(new DomElementAnnotationsManager.DomHighlightingListener() {
      @Override
      public void highlightingFinished(@NotNull DomFileElement element) {
        if (element.isValid()) {
          queueUpdate(DomUtil.getFile(element).getVirtualFile());
        }
      }
    }, this);

    myTree.setPopupGroup(getPopupActions(), DOM_MODEL_TREE_VIEW_POPUP);
  }

  protected boolean isRightFile(final VirtualFile file) {
    return myRootElement == null || (myRootElement.isValid() && file.equals(DomUtil.getFile(myRootElement).getVirtualFile()));
  }

  private void queueUpdate(final VirtualFile file) {
    if (file == null) return;
    if (getProject().isDisposed()) return;
    ApplicationManager.getApplication().invokeLater(() -> {
      if (getProject().isDisposed()) return;
      if (!file.isValid() || isRightFile(file)) {
        myBuilder.updateFromRoot();
      }
    });
  }

  protected boolean isRootVisible() {
    return true;
  }

  public final void updateTree() {
    myBuilder.updateFromRoot();
  }

  public DomElement getRootElement() {
    return myRootElement;
  }

  protected final Project getProject() {
    return myDomManager.getProject();
  }

  public AbstractTreeBuilder getBuilder() {
    return myBuilder;
  }

  @Override
  public void dispose() {
  }

  public SimpleTree getTree() {
    return myTree;
  }

  protected ActionGroup getPopupActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(ActionManager.getInstance().getAction("DomElementsTreeView.TreePopup"));
    group.addSeparator();

    group.add(new ExpandAllAction(myTree));
    group.add(new CollapseAllAction(myTree));

    return group;
  }

  @Override
  @Nullable
  public Object getData(String dataId) {
    if (DATA_KEY.is(dataId)) {
      return this;
    }
    final SimpleNode simpleNode = getTree().getSelectedNode();
    if (simpleNode instanceof AbstractDomElementNode) {
      final DomElement domElement = ((AbstractDomElementNode)simpleNode).getDomElement();
      if (domElement != null && domElement.isValid()) {
        if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
          final XmlElement tag = domElement.getXmlElement();
          if (tag instanceof Navigatable) {
            return tag;
          }
        }
      }
    }
    return null;
  }

  public void setSelectedDomElement(final DomElement domElement) {
    if (domElement == null) return;

    final SimpleNode node = getNodeFor(domElement);

    if (node != null) {
      getTree().setSelectedNode(getBuilder(), node, true);
    }
  }

  @Nullable
  private SimpleNode getNodeFor(final DomElement domElement) {
    return visit((SimpleNode)myBuilder.getTreeStructure().getRootElement(), domElement);
  }

  @Nullable
  private SimpleNode visit(SimpleNode simpleNode, DomElement domElement) {
    boolean validCandidate = false;
    if (simpleNode instanceof AbstractDomElementNode) {
      final DomElement nodeElement = ((AbstractDomElementNode)simpleNode).getDomElement();
      if (nodeElement != null) {
        validCandidate = !(simpleNode instanceof DomElementsGroupNode);
        if (validCandidate && nodeElement.equals(domElement)) {
          return simpleNode;
        }
        if (!(nodeElement instanceof MergedObject) && !isParent(nodeElement, domElement)) {
          return null;
        }
      }
    }
    final Object[] childElements = myBuilder.getTreeStructure().getChildElements(simpleNode);
    if (childElements.length == 0 && validCandidate) { // leaf
      return simpleNode;
    }
    for (Object child: childElements) {
      SimpleNode result = visit((SimpleNode)child, domElement);
      if (result != null) {
        return result;
      }
    }
    return validCandidate ? simpleNode : null;
  }

  private static boolean isParent(final DomElement potentialParent, final DomElement domElement) {
    DomElement currParent = domElement;
    while (currParent != null) {
      if (currParent.equals(potentialParent)) return true;

      currParent = currParent.getParent();
    }
    return false;
  }

}

