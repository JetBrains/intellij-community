package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.packaging.PackagingEditorUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.SourceItemPresentation;
import com.intellij.packaging.ui.SourceItemWeights;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class LibrarySourceItem extends PackagingSourceItem {
  private Library myLibrary;

  public LibrarySourceItem(@NotNull Library library) {
    myLibrary = library;
  }

  @Override
  public SourceItemPresentation createPresentation(@NotNull PackagingEditorContext context) {
    return new LibrarySourceItemPresentation(myLibrary);
  }

  public boolean equals(Object obj) {
    return obj instanceof LibrarySourceItem && myLibrary.equals(((LibrarySourceItem)obj).myLibrary);
  }

  public int hashCode() {
    return myLibrary.hashCode();
  }

  @NotNull 
  public Library getLibrary() {
    return myLibrary;
  }

  @NotNull
  @Override
  public PackagingElementOutputKind getKindOfProducedElements() {
    return getKindForLibrary(myLibrary);
  }

  public static PackagingElementOutputKind getKindForLibrary(final Library library) {
    boolean containsDirectories = false;
    boolean containsJars = false;
    for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
      if (file.isInLocalFileSystem()) {
        containsDirectories = true;
      }
      else {
        containsJars = true;
      }
    }
    return new PackagingElementOutputKind(containsDirectories, containsJars);
  }

  @NotNull
  public List<? extends PackagingElement<?>> createElements(@NotNull PackagingEditorContext context) {
    return PackagingElementFactory.getInstance().createLibraryElements(myLibrary);
  }

  private static class LibrarySourceItemPresentation extends SourceItemPresentation {
    private final Library myLibrary;

    public LibrarySourceItemPresentation(Library library) {
      myLibrary = library;
    }

    @Override
    public String getPresentableName() {
      final String name = myLibrary.getName();
      if (name != null) {
        return name;
      }
      final VirtualFile[] files = myLibrary.getFiles(OrderRootType.CLASSES);
      return files.length > 0 ? files[0].getName() : "Empty Library";
    }

    @Override
    public void render(@NotNull PresentationData presentationData) {
      final String name = myLibrary.getName();
      if (name != null) {
        presentationData.setIcons(Icons.LIBRARY_ICON);
        presentationData.addText(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        presentationData.addText(PackagingEditorUtil.getLibraryTableComment(myLibrary), SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      else {
        final VirtualFile[] files = myLibrary.getFiles(OrderRootType.CLASSES);
        if (files.length > 0) {
          final VirtualFile file = files[0];
          presentationData.setIcons(file.getIcon());
          presentationData.addText(file.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        else {
          presentationData.addText("Empty Library", SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
      }
    }

    @Override
    public int getWeight() {
      return SourceItemWeights.LIBRARY_WEIGHT;
    }
  }
}
