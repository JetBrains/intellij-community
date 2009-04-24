package com.intellij.openapi.roots.ui.configuration.artifacts.dragAndDrop;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.elements.LibraryElementType;
import com.intellij.packaging.ui.PackagingDragAndDropSourceItemsProvider;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class LibrariesSourceItemsProvider extends PackagingDragAndDropSourceItemsProvider {
  public LibrariesSourceItemsProvider() {
    super("Libraries");
  }

  @NotNull
  public Collection<? extends PackagingSourceItem> getSourceItems(PackagingEditorContext editorContext, Artifact artifact) {
    List<PackagingSourceItem> items = new ArrayList<PackagingSourceItem>();
    for (Library library : LibraryElementType.getNotAddedLibraries(editorContext, artifact)) {
      items.add(new LibrarySourceItem(library));
    }
    return items;
  }
}
