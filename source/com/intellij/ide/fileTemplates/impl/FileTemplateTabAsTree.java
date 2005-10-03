package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.UIUtil;

import java.awt.Component;
import java.awt.Font;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;

/**
 * @author Alexey Kudravtsev
 */
abstract class FileTemplateTabAsTree extends FileTemplateTab {
  private JTree myTree;
  private TreeNode myRoot;
  private MyTreeModel myTreeModel;

  protected FileTemplateTabAsTree(String title) {
    super(title);
    myRoot = initModel();
    myTreeModel = new MyTreeModel(myRoot);
    myTree = new JTree(myTreeModel);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(myTree);

    myTree.expandPath(TreeUtil.getPathFromRoot(myRoot));
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setCellRenderer(new MyTreeCellRenderer());
    myTree.expandRow(0);

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        onTemplateSelected();
      }
    });
  }

  protected abstract TreeNode initModel();
  protected static class TreeNode extends DefaultMutableTreeNode {
    private Icon myIcon;
    private final String myTemplate;
    private final TreeNode[] myChildren;

    TreeNode(String name, Icon icon, TreeNode[] children) {
      this(name, icon, children, null);
    }

    TreeNode(Icon icon, String template) {
      this(template, icon, new TreeNode[0], template);
    }

    private TreeNode(String name, Icon icon, TreeNode[] children, String template) {
      super(name);
      myIcon = icon;
      myChildren = children;
      myTemplate = template;
      for (int i = 0; i < children.length; i++) {
        TreeNode child = children[i];
        add(child);
      }
    }

    public Icon getIcon() {
      return myIcon;
    }

    public String getTemplate() {
      return myTemplate;
    }

    public TreeNode[] getChildren() {
      return myChildren;
    }
  }

  private class MyTreeModel extends DefaultTreeModel {
    MyTreeModel(TreeNode root) {
      super(root);
    }
  }

  private class MyTreeCellRenderer extends DefaultTreeCellRenderer {
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

      if (value instanceof TreeNode) {
        final TreeNode node = (TreeNode)value;
        setText((String) node.getUserObject());
        setIcon(node.getIcon());
        setFont(getFont().deriveFont(AllFileTemplatesConfigurable.isInternalTemplate(node.getTemplate(), getTitle()) ? Font.BOLD : Font.PLAIN));
      }
      return this;
    }
  }

  public void removeSelected() {
    // not supported
  }

  protected void initSelection(FileTemplate selection) {
    if (selection != null) {
      selectTemplate(selection);
    }
    else {
      TreeUtil.selectFirstNode(myTree);
    }
  }

  public void selectTemplate(FileTemplate template) {
    String name = template.getName();
    if (template.getExtension() != null && template.getExtension().length() > 0) {
      name += "." + template.getExtension();
    }
    
    final TreeNode node = (TreeNode)TreeUtil.findNodeWithObject(myRoot, name);
    if (node != null) {
      TreeUtil.selectNode(myTree, node);
      onTemplateSelected(); // this is important because we select different Template for the same node
    }
  }

  public FileTemplate getSelectedTemplate() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null) return null;
    final TreeNode node = (TreeNode)selectionPath.getLastPathComponent();
    final String template = node.getTemplate();
    if (template == null) return null;
    return savedTemplates.get(FileTemplateManager.getInstance().getJ2eeTemplate(template));
  }

  public JComponent getComponent() {
    return myTree;
  }

  public void fireDataChanged() {
  }

  public void addTemplate(FileTemplate newTemplate) {
    // not supported
  }
}
