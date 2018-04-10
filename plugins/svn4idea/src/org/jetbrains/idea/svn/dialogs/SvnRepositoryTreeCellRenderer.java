// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
        append(node.getURL().toDecodedString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setIcon(PlatformIcons.FOLDER_ICON);
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
                : PlatformIcons.FOLDER_ICON);
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
