// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;
import org.jetbrains.idea.svn.history.SvnFileRevision;

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

import static org.jetbrains.idea.svn.SvnUtil.createUrl;

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

  @NotNull
  public Project getProject() {
    return myVCS.getProject();
  }

  public void setRepositoryURLs(Url[] urls, final boolean showFiles) {
    setRepositoryURLs(urls, showFiles, null, false);
  }

  public void setRepositoryURLs(Url[] urls,
                                final boolean showFiles,
                                @Nullable NotNullFunction<RepositoryBrowserComponent, Expander> defaultExpanderFactory,
                                boolean expandFirst) {
    RepositoryTreeModel model = new RepositoryTreeModel(myVCS, showFiles, this);

    if (defaultExpanderFactory != null) {
      model.setDefaultExpanderFactory(defaultExpanderFactory);
    }

    model.setRoots(urls);
    Disposer.register(this, model);
    myRepositoryTree.setModel(model);

    if (expandFirst) {
      myRepositoryTree.expandRow(0);
    }
  }

  public void setRepositoryURL(Url url,
                               boolean showFiles,
                               final NotNullFunction<RepositoryBrowserComponent, Expander> defaultExpanderFactory) {
    RepositoryTreeModel model = new RepositoryTreeModel(myVCS, showFiles, this);

    model.setDefaultExpanderFactory(defaultExpanderFactory);

    model.setSingleRoot(url);
    Disposer.register(this, model);
    myRepositoryTree.setModel(model);
    myRepositoryTree.setRootVisible(true);
    myRepositoryTree.setSelectionRow(0);
  }

  public void setRepositoryURL(Url url, boolean showFiles) {
    RepositoryTreeModel model = new RepositoryTreeModel(myVCS, showFiles, this);
    model.setSingleRoot(url);
    Disposer.register(this, model);
    myRepositoryTree.setModel(model);
    myRepositoryTree.setRootVisible(true);
    myRepositoryTree.setSelectionRow(0);
  }

  public void expandNode(@NotNull final TreeNode treeNode) {
    final TreeNode[] pathToNode = ((RepositoryTreeModel)myRepositoryTree.getModel()).getPathToRoot(treeNode);

    if ((pathToNode != null) && (pathToNode.length > 0)) {
      final TreePath treePath = new TreePath(pathToNode);
      myRepositoryTree.expandPath(treePath);
    }
  }

  public Collection<TreeNode> getExpandedSubTree(@NotNull final TreeNode treeNode) {
    final TreeNode[] pathToNode = ((RepositoryTreeModel)myRepositoryTree.getModel()).getPathToRoot(treeNode);

    final Enumeration<TreePath> expanded = myRepositoryTree.getExpandedDescendants(new TreePath(pathToNode));

    final List<TreeNode> result = new ArrayList<>();
    if (expanded != null) {
      while (expanded.hasMoreElements()) {
        final TreePath treePath = expanded.nextElement();
        result.add((TreeNode)treePath.getLastPathComponent());
      }
    }
    return result;
  }

  public boolean isExpanded(@NotNull final TreeNode treeNode) {
    final TreeNode[] pathToNode = ((RepositoryTreeModel)myRepositoryTree.getModel()).getPathToRoot(treeNode);

    return (pathToNode != null) && (pathToNode.length > 0) && myRepositoryTree.isExpanded(new TreePath(pathToNode));
  }

  public void addURL(String url) {
    try {
      ((RepositoryTreeModel)myRepositoryTree.getModel()).addRoot(createUrl(url));
    }
    catch (SvnBindException ignored) {
    }
  }

  public void removeURL(String url) {
    try {
      ((RepositoryTreeModel)myRepositoryTree.getModel()).removeRoot(createUrl(url));
    }
    catch (SvnBindException ignored) {
    }
  }

  @Nullable
  public DirectoryEntry getSelectedEntry() {
    TreePath selection = myRepositoryTree.getSelectionPath();
    if (selection == null) {
      return null;
    }
    Object element = selection.getLastPathComponent();
    if (element instanceof RepositoryTreeNode) {
      RepositoryTreeNode node = (RepositoryTreeNode)element;
      return node.getSVNDirEntry();
    }
    return null;
  }

  @Nullable
  public String getSelectedURL() {
    Url selectedUrl = getSelectedSVNURL();
    return selectedUrl == null ? null : selectedUrl.toString();
  }

  @Nullable
  public Url getSelectedSVNURL() {
    TreePath selection = myRepositoryTree.getSelectionPath();
    if (selection == null) {
      return null;
    }
    Object element = selection.getLastPathComponent();
    if (element instanceof RepositoryTreeNode) {
      RepositoryTreeNode node = (RepositoryTreeNode)element;
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
    TreeSpeedSearch search = new TreeSpeedSearch(myRepositoryTree, o -> {
      Object component = o.getLastPathComponent();
      if (component instanceof RepositoryTreeNode) {
        return ((RepositoryTreeNode)component).getURL().toDecodedString();
      }
      return null;
    });
    search.setComparator(new SpeedSearchComparator(false, true));

    EditSourceOnDoubleClickHandler.install(myRepositoryTree);
  }

  @Nullable
  public RepositoryTreeNode getSelectedNode() {
    TreePath selection = myRepositoryTree.getSelectionPath();
    if (selection != null && selection.getLastPathComponent() instanceof RepositoryTreeNode) {
      return (RepositoryTreeNode)selection.getLastPathComponent();
    }
    return null;
  }

  public void setSelectedNode(@NotNull final TreeNode node) {
    final TreeNode[] pathNodes = ((RepositoryTreeModel)myRepositoryTree.getModel()).getPathToRoot(node);
    myRepositoryTree.setSelectionPath(new TreePath(pathNodes));
  }

  @Nullable
  public VirtualFile getSelectedVcsFile() {
    final RepositoryTreeNode node = getSelectedNode();
    if (node == null) return null;

    DirectoryEntry entry = node.getSVNDirEntry();
    if (entry == null || !entry.isFile()) {
      return null;
    }

    String name = entry.getName();
    FileTypeManager manager = FileTypeManager.getInstance();

    if (entry.getName().lastIndexOf('.') > 0 && !manager.getFileTypeByFileName(name).isBinary()) {
      Url url = node.getURL();
      SvnFileRevision revision =
        new SvnFileRevision(myVCS, Revision.UNDEFINED, Revision.HEAD, url, entry.getAuthor(), entry.getDate(), null, null);

      return new VcsVirtualFile(node.getSVNDirEntry().getName(), revision, VcsFileSystem.getInstance());
    }
    else {
      return null;
    }
  }

  @Nullable
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      final Project project = myVCS.getProject();
      if (project == null || project.isDefault()) {
        return null;
      }
      final VirtualFile vcsFile = getSelectedVcsFile();

      // do not return OpenFileDescriptor instance here as in that case SelectInAction will be enabled and its invocation (using keyboard)
      // will raise error - see IDEA-104113 - because of the following operations inside SelectInAction.actionPerformed():
      // - at first VcsVirtualFile content will be loaded which for svn results in showing progress dialog
      // - then DataContext from SelectInAction will still be accessed which results in error as current event count has already changed
      // (because of progress dialog)
      return vcsFile != null ? new NavigatableAdapter() {
        @Override
        public void navigate(boolean requestFocus) {
          navigate(project, vcsFile, requestFocus);
        }
      } : null;
    }
    else if (CommonDataKeys.PROJECT.is(dataId)) {
      return myVCS.getProject();
    }
    return null;
  }

  public void dispose() {
  }

  public void setLazyLoadingExpander(final NotNullFunction<RepositoryBrowserComponent, Expander> expanderFactory) {
    ((RepositoryTreeModel)myRepositoryTree.getModel()).setDefaultExpanderFactory(expanderFactory);
  }
}
