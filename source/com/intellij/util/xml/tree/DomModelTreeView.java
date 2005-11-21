package com.intellij.util.xml.tree;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.IconLoader;
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
import jetbrains.fabrique.ui.treeStructure.actions.CollapseAllAction;
import jetbrains.fabrique.ui.treeStructure.actions.ExpandAllAction;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;

public class DomModelTreeView extends Wrapper {
  private final SimpleTree myTree;
  private final SimpleTreeBuilder myBuilder;
  private DomManager myDomManager;
  private DomEventListener myDomEventListener;
  private DomElement myRootElement;

  public DomModelTreeView(DomElement rootElement) {
    myRootElement = rootElement;
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

    myBuilder = new SimpleTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), treeStructure, WeightBasedComparator.INSTANCE);
    myBuilder.setNodeDescriptorComparator(null);
    myBuilder.initRoot();

    add(myTree, BorderLayout.CENTER);
    add(getToolbarPane(), BorderLayout.NORTH);

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

  protected JComponent getToolbarPane() {
    DefaultActionGroup actions = new DefaultActionGroup();

    actions.add(new ToggleAction("ShowProperties") {
      public void update(final AnActionEvent e) {
        super.update(e);
        e.getPresentation().setIcon(IconLoader.getIcon("/nodes/pointcut.png"));
      }

      public boolean isSelected(AnActionEvent e) {
        final Boolean showGenericValues = myRootElement.getRoot().getUserData(BaseDomElementNode.SHOW_PROPERTIES_KEY);

        return showGenericValues == null ? false: showGenericValues;
      }

      public void setSelected(AnActionEvent e, boolean state) {
        myRootElement.getRoot().putUserData(BaseDomElementNode.SHOW_PROPERTIES_KEY, state);
        myBuilder.updateFromRoot(true);
      }
    });

    actions.add(new ExpandAllAction(myTree));
    actions.add(new CollapseAllAction(myTree));

    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.UNKNOWN, actions, true);

    return actionToolbar.getComponent();
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

