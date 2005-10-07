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

import com.intellij.CommonBundle;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public class SvnRepositoryTreeCellRenderer extends ColoredTreeCellRenderer {


  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (value instanceof DefaultMutableTreeNode) {
      value = ((DefaultMutableTreeNode)value).getUserObject();
    }

    if (value instanceof SVNRepository) {
      append("/", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      setDirectoryIcon(expanded);
    }
    else if (value instanceof SVNDirEntry) {
      append(((SVNDirEntry)value).getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      if (((SVNDirEntry)value).getKind() == SVNNodeKind.FILE) {
        setIcon(FileTypeManager.getInstance().getFileTypeByFileName(((SVNDirEntry)value).getName()).getIcon());
      } else {
        setDirectoryIcon(expanded);
      }

    }
    else if (value == RepositoryTreeModel.LOADING_NODE) {
      setIcon(null);
      append(CommonBundle.getLoadingTreeNodeText(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    else if (value instanceof RepositoryTreeModel.ErrorNode) {
      setIcon(null);
      RepositoryTreeModel.ErrorNode node = (RepositoryTreeModel.ErrorNode)value;
      append(node.getMessage(), node.isError() ?
                                SimpleTextAttributes.ERROR_ATTRIBUTES :  SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    else {
      setIcon(null);
      append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  private void setDirectoryIcon(final boolean expanded) {
    if (expanded) {
      setIcon(Icons.DIRECTORY_OPEN_ICON);
    } else {
      setIcon(Icons.DIRECTORY_CLOSED_ICON);
    }
  }
}
