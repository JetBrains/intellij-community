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
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;

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

  public void setCellRenderer(TreeCellRenderer renderer) {
    myRepositoryTree.setCellRenderer(renderer);
  }

  public void setRepositoryURL(String url, boolean showFiles) {
    if (url == null || "".equals(url)) {
      RepositoryTreeModel.ErrorNode node = new RepositoryTreeModel.ErrorNode(SvnBundle.message("node.text.browser.type.valid.url"),
                                                                             false);
      DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode(node));
      myRepositoryTree.setModel(model);
      myRepositoryTree.setEnabled(false);
    }
    else {
      myRepositoryTree.setRootVisible(true);
      myRepositoryTree.setShowsRootHandles(true);
      myRepositoryTree.setEnabled(true);
      RepositoryTreeModel model = new RepositoryTreeModel(myRepositoryTree, myVCS, url, showFiles);
      myRepositoryTree.setModel(model);
      TreePath selectionPath = model.getRootPath();
      if (selectionPath != null) {
        myRepositoryTree.expandPath(selectionPath);
        myRepositoryTree.setSelectionPath(selectionPath);
      }
      else {
        myRepositoryTree.setSelectionRow(0);
      }
    }
  }

  public SVNDirEntry getSelectedEntry() {
    TreePath selection = myRepositoryTree.getSelectionPath();
    if (selection == null) {
      return null;
    }
    Object element = selection.getLastPathComponent();
    if (element instanceof SVNDirEntry) {
      return (SVNDirEntry)element;
    }
    return null;
  }

  public String getSelectedURL() {
    TreePath selection = myRepositoryTree.getSelectionPath();
    if (selection == null) {
      return null;
    }
    Object element = selection.getLastPathComponent();
    if (element instanceof SVNDirEntry) {
      RepositoryTreeModel model = (RepositoryTreeModel)myRepositoryTree.getModel();
      String rootURL = model.getRootURL();
      if (rootURL != null) {
        String path = SVNEncodingUtil.uriEncode(((SVNDirEntry)element).getRelativePath());
        String url = SVNPathUtil.append(rootURL, path);
        if (url != null && url.endsWith("/") && url.length() > 1) {
          url = url.substring(0, url.length() - 1);
        }
        return url;
      }
    }
    return null;
  }

  public void refresh(SVNDirEntry entry, boolean deleted) {
    if (myRepositoryTree.getModel() instanceof RepositoryTreeModel) {
      RepositoryTreeModel model = (RepositoryTreeModel)myRepositoryTree.getModel();
      model.refresh(entry, deleted);
    }
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
    setLayout(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.insets = new Insets(2, 2, 2, 2);
    gc.weightx = 0;
    gc.weighty = 0;
    gc.gridwidth = 1;
    gc.gridheight = 1;
    gc.gridy = 0;
    gc.gridx = 0;
    gc.anchor = GridBagConstraints.SOUTHWEST;

    JLabel topLabel = new JLabel(SvnBundle.message("label.browser.select.location"));
    add(topLabel, gc);

    gc.gridy += 1;
    gc.gridx = 0;
    gc.gridwidth = 2;
    gc.gridheight = 1;
    gc.weightx = 1;
    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;

    myRepositoryTree = new JTree();
    myRepositoryTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myRepositoryTree.setRootVisible(true);
    JScrollPane scrollPane = new JScrollPane(myRepositoryTree,
                                             JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
    add(scrollPane, gc);
    topLabel.setLabelFor(myRepositoryTree);

    myRepositoryTree.setCellRenderer(new SvnRepositoryTreeCellRenderer());
  }


  public String getRootURL() {
    if (!(myRepositoryTree.getModel() instanceof RepositoryTreeModel)) {
      return null;
    }
    return ((RepositoryTreeModel)myRepositoryTree.getModel()).getRootURL();
  }
}
