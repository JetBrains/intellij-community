package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.deployment.LibraryLink;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
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
  protected String getOutputFileName() {
    return myFile.getName();
  }

  public double getWeight() {
    return PackagingNodeWeights.FILE;
  }

  public void render(final ColoredTreeCellRenderer renderer) {
    renderer.setIcon(myFile.getIcon());
    renderer.append(myFile.getName(), getMainAttributes());
    String name = myLibrary.getName();
    LibraryTable table = myLibrary.getTable();
    if (name != null) {
      StringBuilder comment = new StringBuilder();
      comment.append(" ('").append(name).append("' ");
      comment.append(PackagingEditorUtil.getLibraryTableDisplayName(myLibrary));
      comment.append(")");
      renderer.append(comment.toString(), getCommentAttributes());
    }
    else if (table == null) {
      Module module = ((LibraryImpl)myLibrary).getModule();
      String comment;
      if (module == null) {
        comment = " (" + PackagingEditorUtil.getLibraryTableDisplayName(myLibrary) + ")";
      }
      else {
        comment = " " + ProjectBundle.message("node.text.library.of.module", module.getName());
      }
      renderer.append(comment, getCommentAttributes());
    }
  }

  public VirtualFile getFile() {
    return myFile;
  }
}
