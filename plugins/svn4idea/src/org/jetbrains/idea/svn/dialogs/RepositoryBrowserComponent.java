/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;
import org.jetbrains.idea.svn.history.SvnFileRevision;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

/**
 * @author alex
 */
public class RepositoryBrowserComponent extends JPanel implements Disposable, DataProvider {

  private JTree myRepositoryTree;
  private final SvnVcs myVCS;

  public RepositoryBrowserComponent(@NotNull SvnVcs vcs) {
    myVCS = vcs;
    createComponent();
  }

  public JTree getRepositoryTree() {
    return myRepositoryTree;
  }

  public void setRepositoryURLs(SVNURL[] urls, final boolean showFiles) {
    RepositoryTreeModel model = new RepositoryTreeModel(myVCS, showFiles, this);
    model.setRoots(urls);
    Disposer.register(this, model);
    myRepositoryTree.setModel(model);
  }

  public void setRepositoryURL(SVNURL url, boolean showFiles, final NotNullFunction<RepositoryBrowserComponent, Expander> defaultExpanderFactory) {
    RepositoryTreeModel model = new RepositoryTreeModel(myVCS, showFiles, this);

    model.setDefaultExpanderFactory(defaultExpanderFactory);

    model.setSingleRoot(url);
    Disposer.register(this, model);
    myRepositoryTree.setModel(model);
    myRepositoryTree.setRootVisible(true);
    myRepositoryTree.setSelectionRow(0);
  }

  public void setRepositoryURL(SVNURL url, boolean showFiles) {
    RepositoryTreeModel model = new RepositoryTreeModel(myVCS, showFiles, this);
    model.setSingleRoot(url);
    Disposer.register(this, model);
    myRepositoryTree.setModel(model);
    myRepositoryTree.setRootVisible(true);
    myRepositoryTree.setSelectionRow(0);
  }

  public void expandNode(@NotNull final TreeNode treeNode) {
    final TreeNode[] pathToNode = ((RepositoryTreeModel) myRepositoryTree.getModel()).getPathToRoot(treeNode);

    if ((pathToNode != null) && (pathToNode.length > 0)) {
      final TreePath treePath = new TreePath(pathToNode);
      myRepositoryTree.expandPath(treePath);
    }
  }

  public Collection<TreeNode> getExpandedSubTree(@NotNull final TreeNode treeNode) {
    final TreeNode[] pathToNode = ((RepositoryTreeModel) myRepositoryTree.getModel()).getPathToRoot(treeNode);

    final Enumeration<TreePath> expanded = myRepositoryTree.getExpandedDescendants(new TreePath(pathToNode));

    final List<TreeNode> result = new ArrayList<TreeNode>();
    while (expanded.hasMoreElements()) {
      final TreePath treePath = expanded.nextElement();
      result.add((TreeNode) treePath.getLastPathComponent());
    }
    return result;
  }

  public boolean isExpanded(@NotNull final TreeNode treeNode) {
    final TreeNode[] pathToNode = ((RepositoryTreeModel) myRepositoryTree.getModel()).getPathToRoot(treeNode);

    return (pathToNode != null) && (pathToNode.length > 0) && myRepositoryTree.isExpanded(new TreePath(pathToNode));
  }

  public void addURL(String url) {
    try {
      ((RepositoryTreeModel) myRepositoryTree.getModel()).addRoot(SVNURL.parseURIEncoded(url));
    } catch (SVNException e) {
      //
    }
  }

  public void removeURL(String url) {
    try {
      ((RepositoryTreeModel) myRepositoryTree.getModel()).removeRoot(SVNURL.parseURIEncoded(url));
    } catch (SVNException e) {
      //
    }
  }

  @Nullable
  public SVNDirEntry getSelectedEntry() {
    TreePath selection = myRepositoryTree.getSelectionPath();
    if (selection == null) {
      return null;
    }
    Object element = selection.getLastPathComponent();
    if (element instanceof RepositoryTreeNode) {
      RepositoryTreeNode node = (RepositoryTreeNode) element;
      return node.getSVNDirEntry();
    }
    return null;
  }

  @Nullable
  public String getSelectedURL() {
    SVNURL selectedUrl = getSelectedSVNURL();
    return selectedUrl == null ? null : selectedUrl.toString();
  }

  @Nullable
  public SVNURL getSelectedSVNURL() {
    TreePath selection = myRepositoryTree.getSelectionPath();
    if (selection == null) {
      return null;
    }
    Object element = selection.getLastPathComponent();
    if (element instanceof RepositoryTreeNode) {
      RepositoryTreeNode node = (RepositoryTreeNode) element;
      return node.getURL();
    }
    return null;
  }

  public void addChangeListener(TreeSelectionListener l) {
    myRepositoryTree.addTreeSelectionListener(l);
  }

  public void removeChangeListener(TreeSelectionListener l) {
    myRepositoryTree.removeTreeSelectionListener(l);
  }

  public Component getPreferredFocusedComponent() {
    return myRepositoryTree;
  }

  private void createComponent() {
    setLayout(new BorderLayout());
    myRepositoryTree = new Tree();
    myRepositoryTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myRepositoryTree.setRootVisible(false);
    myRepositoryTree.setShowsRootHandles(true);
    JScrollPane scrollPane =
      ScrollPaneFactory.createScrollPane(myRepositoryTree, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
    add(scrollPane, BorderLayout.CENTER);
    myRepositoryTree.setCellRenderer(new SvnRepositoryTreeCellRenderer());

    EditSourceOnDoubleClickHandler.install(myRepositoryTree);
  }

  @Nullable
  public RepositoryTreeNode getSelectedNode() {
    TreePath selection = myRepositoryTree.getSelectionPath();
    if (selection != null && selection.getLastPathComponent() instanceof RepositoryTreeNode) {
      return (RepositoryTreeNode) selection.getLastPathComponent();
    }
    return null;
  }

  public void setSelectedNode(@NotNull final TreeNode node) {
    final TreeNode[] pathNodes = ((RepositoryTreeModel) myRepositoryTree.getModel()).getPathToRoot(node);
    myRepositoryTree.setSelectionPath(new TreePath(pathNodes));
  }

  @Nullable
  public VirtualFile getSelectedVcsFile() {
    final RepositoryTreeNode node = getSelectedNode();
    if (node == null) return null;

    SVNDirEntry entry = node.getSVNDirEntry();
    if (entry == null || entry.getKind() != SVNNodeKind.FILE) {
      return null;
    }

    String name = entry.getName();
    FileTypeManager manager = FileTypeManager.getInstance();

    if (entry.getName().lastIndexOf('.') > 0 && !manager.getFileTypeByFileName(name).isBinary()) {
      SVNURL url = node.getURL();
      SVNRevision rev = SVNRevision.create(entry.getRevision());
      final SvnFileRevision revision = new SvnFileRevision(myVCS, SVNRevision.UNDEFINED, rev, url.toString(),
              entry.getAuthor(), entry.getDate(), null, null);

      return new VcsVirtualFile(node.getSVNDirEntry().getName(), revision, VcsFileSystem.getInstance());
    } else {
      return null;
    }
  }

  @Nullable
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.NAVIGATABLE.is(dataId)) {
      final Project project = myVCS.getProject();
      if (project == null || project.isDefault()) {
        return null;
      }
      final VirtualFile vcsFile = getSelectedVcsFile();
      return vcsFile != null ? new OpenFileDescriptor(project, vcsFile) : null;
    } else if (PlatformDataKeys.PROJECT.is(dataId)) {
      return myVCS.getProject();
    }
    return null;
  }

  public void dispose() {
  }

  public void setLazyLoadingExpander(final NotNullFunction<RepositoryBrowserComponent, Expander> expanderFactory) {
    ((RepositoryTreeModel) myRepositoryTree.getModel()).setDefaultExpanderFactory(expanderFactory);
  }
}
