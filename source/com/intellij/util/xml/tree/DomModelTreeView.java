package com.intellij.util.xml.tree;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomEventListener;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.events.*;
import jetbrains.fabrique.ui.treeStructure.*;
import jetbrains.fabrique.ui.treeStructure.actions.CollapseAllAction;
import jetbrains.fabrique.ui.treeStructure.actions.ExpandAllAction;

import javax.swing.*;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Map;
import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

public class DomModelTreeView extends Wrapper implements DataProvider {
  public static String DOM_MODEL_TREE_VIEW_KEY = "DOM_MODEL_TREE_VIEW_KEY";
  public static String DOM_MODEL_TREE_VIEW_POPUP = "DOM_MODEL_TREE_VIEW_POPUP";

  private final SimpleTree myTree;
  private final SimpleTreeBuilder myBuilder;
  private DomManager myDomManager;
  private DomEventListener myDomEventListener;
  private DomElement myRootElement;

  public DomModelTreeView(DomElement rootElement) {
    this(rootElement, false);
  }

  public DomModelTreeView(DomElement rootElement, boolean isRootVisible) {
    myRootElement = rootElement;
    myTree = new SimpleTree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myTree.setRootVisible(isRootVisible);
    myTree.setShowsRootHandles(true);

    ToolTipManager.sharedInstance().registerComponent(myTree);
    TreeUtil.installActions(myTree);

    final SimpleTreeStructure treeStructure = rootElement != null ? getTreeStructure(rootElement) : new SimpleTreeStructure() {
      public Object getRootElement() {
        return null;
      }
    };

    myBuilder = new SimpleTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), treeStructure, WeightBasedComparator.INSTANCE);
    myBuilder.setNodeDescriptorComparator(null);
    myBuilder.initRoot();

    add(myTree, BorderLayout.CENTER);

    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      public void treeExpanded(TreeExpansionEvent event) {
        final SimpleNode simpleNode = myTree.getNodeFor(event.getPath());

        if(simpleNode instanceof AbstractDomElementNode) {
          ((AbstractDomElementNode)simpleNode).setExpanded(true);
        }
      }

      public void treeCollapsed(TreeExpansionEvent event) {
         final SimpleNode simpleNode = myTree.getNodeFor(event.getPath());

        if(simpleNode instanceof AbstractDomElementNode) {
          ((AbstractDomElementNode)simpleNode).setExpanded(false);
          simpleNode.update();
        }
      }
    });

    myDomEventListener = new DomEventListener() {

      public void valueChanged(TagValueChangeEvent event) {
        super.valueChanged(event);
      }

      public void elementDefined(ElementDefinedEvent event) {
        super.elementDefined(event);
      }

      public void elementUndefined(ElementUndefinedEvent event) {
        super.elementUndefined(event);
      }

      public void elementChanged(ElementChangedEvent event) {
        super.elementChanged(event);
      }

      public void childAdded(CollectionElementAddedEvent event) {
        super.childAdded(event);
      }

      public void childRemoved(CollectionElementRemovedEvent event) {
        super.childRemoved(event);
      }

      public void eventOccured(DomEvent event) {
        myBuilder.updateFromRoot(false);
      }
    };
    myDomManager = rootElement.getManager();
    myDomManager.addDomEventListener(myDomEventListener);

    myTree.setPopupGroup(getPopupActions(), DOM_MODEL_TREE_VIEW_POPUP);
  }

  public DomElement getRootElement() {
    return myRootElement;
  }

  protected SimpleTreeStructure getTreeStructure(final DomElement rootDomElement) {
    return new DomModelTreeStructure(rootDomElement);
  }

  public SimpleTreeBuilder getBuilder() {
    return myBuilder;
  }

  public void dispose() {
    myDomManager.removeDomEventListener(myDomEventListener);
  }

  public SimpleTree getTree() {
    return myTree;
  }

  protected ActionGroup getPopupActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(ActionManagerEx.getInstance().getAction("DomElementsTreeView.TreePopup"));
    group.addSeparator();

    group.add(new ExpandAllAction(myTree));
    group.add(new CollapseAllAction(myTree));

    return group;
  }

  @Nullable
  public Object getData(String dataId) {
    if (DOM_MODEL_TREE_VIEW_KEY.equals(dataId)) {
      return this;
    }
    return null;
  }

  public void setSelectedDomElement(final DomElement domElement) {
    if(domElement == null) return;

    final java.util.List<SimpleNode> parentsNodes = new ArrayList<SimpleNode>();

    SimpleNodeVisitor visitor = new SimpleNodeVisitor() {
      public boolean accept(SimpleNode simpleNode) {
        if(simpleNode instanceof BaseDomElementNode) {
          final DomElement nodeElement = ((AbstractDomElementNode)simpleNode).getDomElement();
          if(isParent(nodeElement, domElement)) {
             parentsNodes.add(simpleNode);
          }
        }
        return false;
      }

      private boolean isParent(final DomElement potentialParent, final DomElement domElement) {
        DomElement currParent = domElement;
        while(currParent != null) {
          if(currParent.equals(potentialParent)) return true;

          currParent = currParent.getParent();
        }
        return false;
      }
    };

    getTree().accept(getBuilder(), visitor);
    if(parentsNodes.size() > 0) {
        getTree().setSelectedNode(getBuilder(), parentsNodes.get(parentsNodes.size()-1), true);
    }
  };
}

