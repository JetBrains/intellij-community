package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ChooseElementsDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
class ChooseLibrariesDialog extends ChooseElementsDialog<Library> {

  public ChooseLibrariesDialog(JComponent component, String title, List<Library> items) {
    super(component, items, title, true);
  }

  protected String getItemText(final Library item) {
    return PackagingEditorUtil.getLibraryItemText(item);
  }

  protected Icon getItemIcon(final Library item) {
    if (item.getName() != null) {
      return Icons.LIBRARY_ICON;
    }
    VirtualFile[] files = item.getFiles(OrderRootType.CLASSES);
    if (files.length == 1) {
      return files[0].getFileType().getIcon();
    }
    return Icons.LIBRARY_ICON;
  }
}
