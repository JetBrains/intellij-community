package org.jetbrains.idea.svn.dialogs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;

public class RepositoryTreeNode implements TreeNode {

  private TreeNode myParentNode;
  private SVNRepository myRepository;
  private List myChildren;
  private RepositoryTreeModel myModel;
  private String myPath;
  private SVNURL myURL;
  private Object myUserObject;

  public RepositoryTreeNode(RepositoryTreeModel model, TreeNode parentNode, SVNRepository repository, SVNURL url, Object userObject) {
    myParentNode = parentNode;
    myRepository = repository;
    myURL = url;
    myPath = url.getPath().substring(myRepository.getLocation().getPath().length());
    if (myPath.startsWith("/")) {
      myPath = myPath.substring(1);
    }
    myModel = model;
    myUserObject = userObject;
  }

  public Object getUserObject() {
    return myUserObject;
  }

  public int getChildCount() {
    return getChildren().size();
  }

  public Enumeration children() {
    return Collections.enumeration(getChildren());
  }

  public TreeNode getChildAt(int childIndex) {
    return (TreeNode) getChildren().get(childIndex);
  }

  public int getIndex(TreeNode node) {
    return getChildren().indexOf(node);
  }

  public boolean getAllowsChildren() {
    return !isLeaf();
  }

  public boolean isLeaf() {
    return myUserObject instanceof SVNDirEntry ? ((SVNDirEntry) myUserObject).getKind() == SVNNodeKind.FILE : false;
  }

  public TreeNode getParent() {
    return myParentNode;
  }

  public void reload() {
    // couldn't do that when 'loading' is in progress.
    myChildren = null;
    myModel.reload(this);
  }

  public String toString() {
    if (myParentNode instanceof RepositoryTreeRootNode) {
      return myURL.toString();
    }
    return SVNPathUtil.tail(myURL.getPath());
  }

  protected List getChildren() {
    if (myChildren == null) {
      myChildren = new ArrayList();
      myChildren.add(new DefaultMutableTreeNode("Loading"));
      loadChildren();
    }
    return myChildren;
  }

  protected void loadChildren() {
    Runnable loader = new Runnable() {
      public void run() {
        Collection entries = new TreeSet();
        try {
          entries = myRepository.getDir(myPath, -1, null, entries);
        } catch (SVNException e) {
          final SVNErrorMessage err = e.getErrorMessage();
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              // could be null if refresh was called during 'loading'.
              if (myChildren != null) {
                myChildren.clear();
                myChildren.add(new DefaultMutableTreeNode(err));
              }
              myModel.reload(RepositoryTreeNode.this);
            }
          });
          return;
        }
        // create new node for each entry, then update browser in a swing thread.
        final List nodes = new ArrayList();
        for (Iterator iter = entries.iterator(); iter.hasNext();) {
          SVNDirEntry entry = (SVNDirEntry) iter.next();
          if (!myModel.isShowFiles() && entry.getKind() != SVNNodeKind.DIR) {
            continue;
          }
          nodes.add(new RepositoryTreeNode(myModel, RepositoryTreeNode.this, myRepository, entry.getURL(), entry));
        }
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            // could be null if refresh was called during 'loading'.
            if (myChildren != null) {
              myChildren.clear();
              myChildren.addAll(nodes);
            }
            myModel.reload(RepositoryTreeNode.this);
          }
        });
      }
    };
    new Thread(loader).start();
  }

  public SVNURL getURL() {
    return myURL;
  }

  public SVNDirEntry getSVNDirEntry() {
    if (myUserObject instanceof SVNDirEntry) {
      return (SVNDirEntry) myUserObject;
    }
    return null;
  }

  public SVNRepository getRepository() {
    return myRepository;
  }
}
