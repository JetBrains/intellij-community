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

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.idea.svn.browse.DirectoryEntry;

import javax.swing.*;

public class SvnRepositoryTreeCellRenderer extends ColoredTreeCellRenderer {

  private boolean myIsShowDetails;


  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    setIcon(null);
    if (value instanceof RepositoryTreeNode) {
      RepositoryTreeNode node = (RepositoryTreeNode) value;
      if (node.getSVNDirEntry() == null) {
        append(node.getURL().toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setIcon(PlatformIcons.DIRECTORY_CLOSED_ICON);
      } else {
        String name = node.getSVNDirEntry().getName();
        append(name, node.isCached() ? SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        if (myIsShowDetails) {
          DirectoryEntry entry = node.getSVNDirEntry();
          append(" " + entry.getRevision(), SimpleTextAttributes.GRAY_ATTRIBUTES);
          if (entry.getAuthor() != null) {
            append(" " + entry.getAuthor(), SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
          }
          if (entry.getDate() != null) {
            append(" " + DateFormatUtil.formatPrettyDateTime(entry.getDate()), SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
        }
        setIcon(node.getSVNDirEntry().isFile()
                ? FileTypeManager.getInstance().getFileTypeByFileName(name).getIcon()
                : PlatformIcons.DIRECTORY_CLOSED_ICON);
      }
    }
    else if (value instanceof SimpleTextNode) {
      SimpleTextNode node = (SimpleTextNode)value;

      append(node.getText(), node.isError() ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  public void setShowDetails(boolean state) {
    myIsShowDetails = state;
  }
}
