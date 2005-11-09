package com.intellij.util.xml.tree;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomEventListener;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.events.DomEvent;
import jetbrains.fabrique.ui.treeStructure.SimpleTree;
import jetbrains.fabrique.ui.treeStructure.SimpleTreeBuilder;
import jetbrains.fabrique.ui.treeStructure.WeightBasedComparator;
import jetbrains.fabrique.ui.treeStructure.SimpleTreeStructure;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class DomModelTreeView extends Wrapper {
  private SimpleTree myTree;

  private SimpleTreeBuilder myBuilder;

  public DomModelTreeView(Project project, DomElement rootElement) {
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
    myBuilder.initRoot();

    add(myTree);

    DomManager.getDomManager(project).addDomEventListener(new DomEventListener() {
      public void eventOccured(DomEvent event) {
        super.eventOccured(event);

        myBuilder.updateFromRoot(true);
      }
    });
  }

  protected SimpleTreeStructure getTreeStructure(final DomElement rootDomElement) {
    return new DomModelTreeStructure(rootDomElement.getRoot());
  }

  public SimpleTreeBuilder getBuilder() {
    return myBuilder;
  }

}

