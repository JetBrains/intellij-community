/**
 * @author cdr
 */
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.openapi.util.Ref;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.Enumeration;
import java.util.List;

public abstract class IntentionSettingsTree {
  private JComponent myComponent;
  private CheckboxTree myTree;

  public IntentionSettingsTree() {
    initTree();
  }

  public JComponent getComponent() {
    return myComponent;
  }

  private void initTree() {
    myTree = new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer() {
      public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        CheckedTreeNode node = (CheckedTreeNode)value;
        SimpleTextAttributes attributes = node.getUserObject() instanceof IntentionActionMetaData ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
        getTextRenderer().append(getNodeText(node), attributes);
      }
    }, new CheckedTreeNode(null)){
      protected void checkNode(CheckedTreeNode node, boolean checked) {
        super.checkNode(node,checked);
        checkRecursively(node, checked);
        updateCheckMarkInParents(node);
      }
    };

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        TreePath path = e.getPath();
        Object userObject = ((CheckedTreeNode)path.getLastPathComponent()).getUserObject();
        selectionChanged(userObject);
      }
    });
    //myTree.addTreeExpansionListener(new TreeExpansionListener() {
    //  public void treeCollapsed(TreeExpansionEvent event) {
    //    treeExpansionChanged();
    //  }
    //
    //  public void treeExpanded(TreeExpansionEvent event) {
    //    treeExpansionChanged();
    //  }
    //
    //  private void treeExpansionChanged() {
    //    TreePath path = myTree.getSelectionPath();
    //    if (path != null) {
    //      Object userObject = ((CheckedTreeNode)path.getLastPathComponent()).getUserObject();
    //      selectionChanged(userObject);
    //    }
    //  }
    //});

    myComponent = new JScrollPane(myTree);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myTree.setSelectionRow(0);
      }
    });
  }

  private static void updateCheckMarkInParents(CheckedTreeNode node) {
    while (node.getParent() != null) {
      final CheckedTreeNode parent = (CheckedTreeNode)node.getParent();
      parent.setChecked(true);
      visitChildren(parent, new CheckedNodeVisitor() {
        public void visit(CheckedTreeNode node) {
          if (!node.isChecked()) {
            parent.setChecked(false);
          }
        }
      });
      node = parent;
    }
  }

  protected abstract void selectionChanged(Object selected);

  public void reset(List<IntentionActionMetaData> intentionsToShow) {
    CheckedTreeNode root = new CheckedTreeNode(null);
    final DefaultTreeModel treeModel = (DefaultTreeModel)myTree.getModel();

    for (int i = 0; i < intentionsToShow.size(); i++) {
      final IntentionActionMetaData metaData = intentionsToShow.get(i);
      String[] category = metaData.myCategory;
      CheckedTreeNode node = root;
      for (int j = 0; j < category.length; j++) {
        final String name = category[j];
        CheckedTreeNode child = findChild(node, name);
        if (child == null) {
          CheckedTreeNode newChild = new CheckedTreeNode(name);
          treeModel.insertNodeInto(newChild, node, node.getChildCount());
          child = newChild;
        }
        node = child;
      }
      CheckedTreeNode newChild = new CheckedTreeNode(metaData);
      treeModel.insertNodeInto(newChild, node, node.getChildCount());
    }
    resetCheckMark(root);
    treeModel.setRoot(root);
    treeModel.nodeChanged(root);
    TreeUtil.expandAll(myTree);
  }

  private CheckedTreeNode getRoot() {
    CheckedTreeNode root = (CheckedTreeNode)myTree.getModel().getRoot();
    return root;
  }

  private boolean resetCheckMark(final CheckedTreeNode root) {
    Object userObject = root.getUserObject();
    if (userObject instanceof IntentionActionMetaData) {
      IntentionActionMetaData metaData = (IntentionActionMetaData)userObject;
      boolean enabled = IntentionManagerSettings.getInstance().isEnabled(metaData.myFamily);
      root.setChecked(enabled);
      return enabled;
    }
    else {
      root.setChecked(true);
      visitChildren(root, new CheckedNodeVisitor() {
        public void visit(CheckedTreeNode node) {
          if (!resetCheckMark(node)) {
            root.setChecked(false);
          }
        }
      });
      return root.isChecked();
    }
  }

  private void checkRecursively(CheckedTreeNode root, final boolean check) {
    Object userObject = root.getUserObject();
    root.setChecked(check);
    if (!(userObject instanceof IntentionActionMetaData)) {
      visitChildren(root, new CheckedNodeVisitor() {
        public void visit(CheckedTreeNode node) {
          checkRecursively(node, check);
        }
      });
    }
  }

  private static CheckedTreeNode findChild(CheckedTreeNode node, final String name) {
    final Ref<CheckedTreeNode> found = new Ref<CheckedTreeNode>();
    visitChildren(node, new CheckedNodeVisitor() {
      public void visit(CheckedTreeNode node) {
        String text = getNodeText(node);
        if (name.equals(text)) {
          found.set(node);
        }
      }
    });
    return found.get();
  }

  private static String getNodeText(CheckedTreeNode node) {
    final Object userObject = node.getUserObject();
    String text;
    if (userObject instanceof String) {
      text = (String)userObject;
    }
    else if (userObject instanceof IntentionActionMetaData) {
      text = ((IntentionActionMetaData)userObject).myFamily;
    }
    else {
      text = "???";
    }
    return text;
  }

  public void apply() {
    CheckedTreeNode root = getRoot();
    apply(root);
  }

  private void apply(CheckedTreeNode root) {
    Object userObject = root.getUserObject();
    if (userObject instanceof IntentionActionMetaData) {
      IntentionActionMetaData actionMetaData = (IntentionActionMetaData)userObject;
      IntentionManagerSettings.getInstance().setEnabled(actionMetaData.myFamily, root.isChecked());
    }
    else {
      visitChildren(root, new CheckedNodeVisitor() {
        public void visit(CheckedTreeNode node) {
          apply(node);
        }
      });
    }
  }

  public boolean isModified() {
    return isModified(getRoot());
  }

  private boolean isModified(CheckedTreeNode root) {
    Object userObject = root.getUserObject();
    if (userObject instanceof IntentionActionMetaData) {
      IntentionActionMetaData actionMetaData = (IntentionActionMetaData)userObject;
      boolean enabled = IntentionManagerSettings.getInstance().isEnabled(actionMetaData.myFamily);
      return enabled != root.isChecked();
    }
    else {
      final boolean[] modified = new boolean[] { false };
      visitChildren(root, new CheckedNodeVisitor() {
        public void visit(CheckedTreeNode node) {
          modified[0] |= isModified(node);
        }
      });
      return modified[0];
    }
  }

  public void dispose() {
  }

  interface CheckedNodeVisitor {
    void visit(CheckedTreeNode node);
  }
  private static void visitChildren(CheckedTreeNode node, CheckedNodeVisitor visitor) {
    Enumeration children = node.children();
    while (children.hasMoreElements()) {
      final CheckedTreeNode child = (CheckedTreeNode)children.nextElement();
      visitor.visit(child);
    }
  }
}