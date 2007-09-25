package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.*;

public class RepositoryTreeNode implements TreeNode, Disposable {

  private final TreeNode myParentNode;
  private final SVNRepository myRepository;
  private List<TreeNode> myChildren;
  private final RepositoryTreeModel myModel;
  private String myPath;
  private final SVNURL myURL;
  private final Object myUserObject;

  public RepositoryTreeNode(RepositoryTreeModel model, TreeNode parentNode, @NotNull SVNRepository repository,
                            @NotNull SVNURL url, Object userObject) {
    myParentNode = parentNode;
    myRepository = repository;
    myURL = url;
    final SVNURL location = myRepository.getLocation();
    assert location != null;
    myPath = url.getPath().substring(location.getPath().length());
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
    if (isLeaf()) {
      ((RepositoryTreeNode) getParent()).reload();
    }
  }

  public String toString() {
    if (myParentNode instanceof RepositoryTreeRootNode) {
      return myURL.toString();
    }
    return SVNPathUtil.tail(myURL.getPath());
  }

  private List getChildren() {
    if (myChildren == null) {
      myChildren = new ArrayList<TreeNode>();
      myChildren.add(new DefaultMutableTreeNode("Loading"));
      loadChildren();
    }
    return myChildren;
  }

  private void loadChildren() {
    Runnable loader = new Runnable() {
      public void run() {
        final Collection<SVNDirEntry> entries = new TreeSet<SVNDirEntry>();
        try {
          myRepository.getDir(myPath, -1, null, new ISVNDirEntryHandler() {
            public void handleDirEntry(final SVNDirEntry dirEntry) throws SVNException {
              entries.add(dirEntry);
            }
          });
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
        final List<TreeNode> nodes = new ArrayList<TreeNode>();
        for (final SVNDirEntry entry : entries) {
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
    ApplicationManager.getApplication().executeOnPooledThread(loader);
  }

  public SVNURL getURL() {
    return myURL;
  }

  @Nullable
  public SVNDirEntry getSVNDirEntry() {
    if (myUserObject instanceof SVNDirEntry) {
      return (SVNDirEntry) myUserObject;
    }
    return null;
  }

  public void dispose() {
    myRepository.closeSession();
  }
}
