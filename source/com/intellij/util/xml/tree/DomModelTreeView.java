package com.intellij.util.xml.tree;

import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomEventListener;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.events.DomEvent;
import jetbrains.fabrique.ui.treeStructure.SimpleTree;
import jetbrains.fabrique.ui.treeStructure.SimpleTreeBuilder;
import jetbrains.fabrique.ui.treeStructure.SimpleTreeStructure;
import jetbrains.fabrique.ui.treeStructure.WeightBasedComparator;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class DomModelTreeView extends Wrapper {
  private final SimpleTree myTree;
  private final SimpleTreeBuilder myBuilder;
  private DomManager myDomManager;
  private DomEventListener myDomEventListener;

  public DomModelTreeView(DomElement rootElement) {
    myTree = new SimpleTree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);

    ToolTipManager.sharedInstance().registerComponent(myTree);
    TreeUtil.installActions(myTree);

    final SimpleTreeStructure treeStructure = rootElement != null ? getTreeStructure(rootElement) : new SimpleTreeStructure() {
      public Object getRootElement() {
        return null;
      }
    };

    myBuilder = new SimpleTreeBuilder(myTree, (DefaultTreeModel) myTree.getModel(), treeStructure, WeightBasedComparator.INSTANCE);
    myBuilder.setNodeDescriptorComparator(null);
    myBuilder.initRoot();

    add(myTree);

    myDomEventListener = new DomEventListener() {
      public void eventOccured(DomEvent event) {
        myBuilder.updateFromRoot(true);
      }
    };
    myDomManager = rootElement.getManager();
    myDomManager.addDomEventListener(myDomEventListener);
  }

  protected SimpleTreeStructure getTreeStructure(final DomElement rootDomElement) {
    return new DomModelTreeStructure(rootDomElement.getRoot());
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
}

