/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.jetbrains.annotations.NonNls;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 01.07.2005
 * Time: 19:13:10
 * To change this template use File | Settings | File Templates.
 */
public class RepositoryBrowserComponent extends JPanel {

  private JTree myRepositoryTree;
  private SvnVcs myVCS;

  public RepositoryBrowserComponent(SvnVcs vcs) {
    myVCS = vcs;
    createComponent();
  }

  public JTree getRepositoryTree() {
    return myRepositoryTree;
  }

  public void setRepositoryURLs(SVNURL[] urls, boolean showFiles) {
    RepositoryTreeModel model = new RepositoryTreeModel(myVCS, true);
    model.setRoots(urls);
    myRepositoryTree.setModel(model);
  }

  public void setRepositoryURL(SVNURL url, boolean showFiles) {
    RepositoryTreeModel model = new RepositoryTreeModel(myVCS, showFiles);
    model.setSingleRoot(url);
    myRepositoryTree.setModel(model);
    myRepositoryTree.setRootVisible(true);
    myRepositoryTree.setSelectionRow(0);
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

  public String getSelectedURL() {
    TreePath selection = myRepositoryTree.getSelectionPath();
    if (selection == null) {
      return null;
    }
    Object element = selection.getLastPathComponent();
    if (element instanceof RepositoryTreeNode) {
      RepositoryTreeNode node = (RepositoryTreeNode) element;
      return node.getURL().toString();
    }
    return null;
  }

  public void refresh(SVNDirEntry entry, boolean deleted) {
  }

  public boolean isValid() {
    return getSelectedURL() != null;
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
    myRepositoryTree = new JTree();
    myRepositoryTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myRepositoryTree.setRootVisible(false);
    myRepositoryTree.setShowsRootHandles(true);
    JScrollPane scrollPane = new JScrollPane(myRepositoryTree,
                                             JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
    add(scrollPane, BorderLayout.CENTER);
    myRepositoryTree.setCellRenderer(new SvnRepositoryTreeCellRenderer());
  }

  public RepositoryTreeNode getSelectedNode() {
    TreePath selection = myRepositoryTree.getSelectionPath();
    if (selection != null && selection.getLastPathComponent() instanceof RepositoryTreeNode) {
      return (RepositoryTreeNode) selection.getLastPathComponent();
    }
    return null;
  }

}
