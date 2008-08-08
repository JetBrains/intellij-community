package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.deployment.LibraryLink;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
class LibraryNode extends LibraryNodeBase {
  LibraryNode(final LibraryLink libraryLink, PackagingArtifact owner) {
    super(owner, libraryLink);
  }

  @NotNull
  public String getOutputFileName() {
    return myLibraryLink.getPresentableName();
  }

  public double getWeight() {
    return PackagingNodeWeights.LIBRARY;
  }

  @Override
  public String getSearchName() {
    Library library = myLibraryLink.getLibrary();
    if (library == null) {
      return myLibraryLink.getPresentableName();
    }
    String name = library.getName();
    if (name != null) {
      return name;
    }
    VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
    if (files.length > 0) {
      return files[0].getName();
    }
    return super.getSearchName();
  }

  @Override
  public int compareTo(final PackagingTreeNode node) {
    return getSearchName().compareToIgnoreCase(node.getSearchName());
  }

  public void render(final ColoredTreeCellRenderer renderer) {
    Library library = myLibraryLink.getLibrary();
    if (library == null) {
      String libraryName = myLibraryLink.getPresentableName();
      renderer.append(libraryName, SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
    else {
      PackagingEditorUtil.renderLibraryNode(renderer, library, getMainAttributes(), getCommentAttributes());
    }

  }

  public boolean canNavigate() {
    return true;
  }
}
