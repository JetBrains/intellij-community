package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.impl.ModuleLibraryTable;

/**
 * @author nik
 */
public class PackagingEditorUtil {
  private PackagingEditorUtil() {
  }

  public static String getLibraryTableDisplayName(final Library library) {
    LibraryTable table = library.getTable();
    LibraryTablePresentation presentation = table != null ? table.getPresentation() : ModuleLibraryTable.MODULE_LIBRARY_TABLE_PRESENTATION;
    return presentation.getDisplayName(false);
  }
}
