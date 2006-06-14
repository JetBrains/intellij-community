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
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNDirEntry;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public class SvnRepositoryTreeCellRenderer extends ColoredTreeCellRenderer {

  private boolean myIsShowDetails;


  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    setIcon(null);
    if (value instanceof RepositoryTreeNode) {
      RepositoryTreeNode node = (RepositoryTreeNode) value;
      if (node.getSVNDirEntry() == null) {
        append(node.getURL().toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setDirectoryIcon(expanded);
      } else {
        String name = node.getSVNDirEntry().getName();
        append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        if (myIsShowDetails) {
          SVNDirEntry entry = node.getSVNDirEntry();
          append(" " + entry.getRevision(), SimpleTextAttributes.GRAY_ATTRIBUTES);
          if (entry.getAuthor() != null) {
            append(" " + entry.getAuthor(), SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
          }
          if (entry.getDate() != null) {
            append(" " + entry.getDate().toString(), SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
        }
        if (node.getSVNDirEntry().getKind() == SVNNodeKind.FILE) {
          setIcon(FileTypeManager.getInstance().getFileTypeByFileName(name).getIcon());
        } else {
          setDirectoryIcon(expanded);
        }
      }
    } else if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      if (node.getUserObject() instanceof String) {
        append(CommonBundle.getLoadingTreeNodeText(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      } else if (node.getUserObject() instanceof SVNErrorMessage) {
        append(node.getUserObject().toString(), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }
  }

  private void setDirectoryIcon(final boolean expanded) {
    if (expanded) {
      setIcon(Icons.DIRECTORY_OPEN_ICON);
    } else {
      setIcon(Icons.DIRECTORY_CLOSED_ICON);
    }
  }

  public void setShowDetails(boolean state) {
    myIsShowDetails = state;
  }
}
