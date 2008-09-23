package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.deployment.LibraryLink;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
class LibraryFileNode extends LibraryNodeBase {
  private final VirtualFile myFile;
  private final Library myLibrary;

  LibraryFileNode(final @NotNull VirtualFile file, final @NotNull Library library, @NotNull LibraryLink libraryLink, final @Nullable PackagingArtifact owner) {
    super(owner, libraryLink);
    myFile = file;
    myLibrary = library;
  }

  @NotNull
  public String getOutputFileName() {
    return myFile.getName();
  }

  public double getWeight() {
    return PackagingNodeWeights.FILE;
  }

  public void render(@NotNull final ColoredTreeCellRenderer renderer) {
    PackagingEditorUtil.renderLibraryFile(renderer, myLibrary, myFile, getMainAttributes(), getCommentAttributes());
  }

  public VirtualFile getFile() {
    return myFile;
  }
}
