package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.roots.libraries.LibraryTable;

public interface LibraryTableModifiableModelProvider {

  LibraryTable.ModifiableModel getModifiableModel();

  String getTableLevel();

}
