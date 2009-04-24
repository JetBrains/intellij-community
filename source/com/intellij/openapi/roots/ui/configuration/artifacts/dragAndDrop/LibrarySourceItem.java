package com.intellij.openapi.roots.ui.configuration.artifacts.dragAndDrop;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.packaging.PackagingEditorUtil;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.elements.LibraryPackagingElement;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class LibrarySourceItem extends PackagingSourceItem {
  private Library myLibrary;

  public LibrarySourceItem(Library library) {
    myLibrary = library;
  }

  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    PackagingEditorUtil.renderLibraryNode(renderer, myLibrary, SimpleTextAttributes.REGULAR_ATTRIBUTES, SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  @NotNull
  public PackagingElement createElement() {
    return new LibraryPackagingElement(myLibrary.getTable().getTableLevel(), myLibrary.getName());
  }
}
