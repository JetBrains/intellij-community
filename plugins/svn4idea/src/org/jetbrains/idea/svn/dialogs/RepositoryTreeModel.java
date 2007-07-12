package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

public class RepositoryTreeModel extends DefaultTreeModel implements Disposable {

  private SvnVcs myVCS;
  private boolean myIsShowFiles;

  public RepositoryTreeModel(@NotNull SvnVcs vcs, boolean showFiles) {
    super(null);
    myVCS = vcs;
    myIsShowFiles = showFiles;
  }

  public boolean isShowFiles() {
    return myIsShowFiles;
  }

  public void setShowFiles(boolean showFiles) {
    myIsShowFiles = showFiles;
  }

  public void setRoots(SVNURL[] urls) {
    final RepositoryTreeRootNode rootNode = new RepositoryTreeRootNode(this, urls);
    Disposer.register(this, rootNode);
    setRoot(rootNode);
  }

  public void setSingleRoot(SVNURL url) {
    SVNRepository repos = createRepository(url);
    final RepositoryTreeNode rootNode = new RepositoryTreeNode(this, null, repos, url, url);
    Disposer.register(this, rootNode);
    setRoot(rootNode);
  }

  public boolean hasRoot(SVNURL url) {
    if (getRoot()instanceof RepositoryTreeNode) {
      return ((RepositoryTreeNode) getRoot()).getUserObject().equals(url);
    }
    RepositoryTreeRootNode root = (RepositoryTreeRootNode) getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      RepositoryTreeNode node = (RepositoryTreeNode) root.getChildAt(i);
      if (node.getUserObject().equals(url)) {
        return true;
      }
    }
    return false;
  }

  public void addRoot(SVNURL url) {
    if (!hasRoot(url)) {
      ((RepositoryTreeRootNode) getRoot()).addRoot(url);
    }
  }

  public void removeRoot(SVNURL url) {
    RepositoryTreeRootNode root = (RepositoryTreeRootNode) getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      RepositoryTreeNode node = (RepositoryTreeNode) root.getChildAt(i);
      if (node.getUserObject().equals(url)) {
        root.remove(node);
      }
    }
  }

  public SVNURL[] getRoots() {
    TreeNode root = (TreeNode) getRoot();
    if (root instanceof RepositoryTreeNode) {
      return new SVNURL[]{(SVNURL) ((RepositoryTreeNode) root).getUserObject()};
    }
    SVNURL[] roots = new SVNURL[root.getChildCount()];
    for (int i = 0; i < roots.length; i++) {
      roots[i] = (SVNURL) ((RepositoryTreeNode) root.getChildAt(i)).getUserObject();
    }
    return roots;
  }

  protected SVNRepository createRepository(SVNURL url) {
    try {
      return myVCS.createRepository(url.toString());
    } catch (SVNException e) {
      //
    }
    return null;
  }

  public void dispose() {
  }
}
