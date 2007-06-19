package org.jetbrains.idea.svn.dialogs;

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;

import javax.swing.tree.TreeNode;
import java.text.Collator;
import java.util.*;

public class RepositoryTreeRootNode implements TreeNode {
  private List<TreeNode> myChildren;
  private RepositoryTreeModel myModel;

  public RepositoryTreeRootNode(RepositoryTreeModel model, SVNURL[] urls) {
    myChildren = new ArrayList<TreeNode>();
    myModel = model;

    for (SVNURL url : urls) {
      SVNRepository repos = model.createRepository(url);
      RepositoryTreeNode rootNode = new RepositoryTreeNode(model, this, repos, url, url);
      myChildren.add(rootNode);
    }
    Collections.sort(myChildren, new Comparator<TreeNode>() {
      public int compare(TreeNode o1, TreeNode o2) {
        return Collator.getInstance().compare(o1.toString(), o2.toString());
      }
    });
  }

  public void addRoot(SVNURL url) {
    SVNRepository repos = myModel.createRepository(url);
    RepositoryTreeNode rootNode = new RepositoryTreeNode(myModel, this, repos, url, url);
    myChildren.add(rootNode);
    Collections.sort(myChildren, new Comparator<TreeNode>() {
      public int compare(TreeNode o1, TreeNode o2) {
        return Collator.getInstance().compare(o1.toString(), o2.toString());
      }
    });
    myModel.nodesWereInserted(this, new int[]{myChildren.indexOf(rootNode)});
  }

  public void remove(TreeNode node) {
    int index = getIndex(node);
    myChildren.remove(node);
    myModel.nodesWereRemoved(this, new int[]{index}, new Object[]{node});
  }

  public Enumeration children() {
    return Collections.enumeration(myChildren);
  }

  public boolean getAllowsChildren() {
    return true;
  }

  public TreeNode getChildAt(int childIndex) {
    return myChildren.get(childIndex);
  }

  public int getChildCount() {
    return myChildren.size();
  }

  public int getIndex(TreeNode node) {
    return myChildren.indexOf(node);
  }

  public TreeNode getParent() {
    return null;
  }

  public boolean isLeaf() {
    return false;
  }

}
