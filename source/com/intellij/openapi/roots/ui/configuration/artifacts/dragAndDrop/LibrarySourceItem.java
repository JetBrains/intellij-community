package com.intellij.openapi.roots.ui.configuration.artifacts.dragAndDrop;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.packaging.PackagingEditorUtil;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
  public List<? extends PackagingElement<?>> createElements() {
    return PackagingElementFactory.getInstance().createLibraryElements(myLibrary);
  }
}
