package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.deployment.LibraryLink;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.util.CellAppearanceUtils;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
class LibraryNode extends LibraryNodeBase {
  LibraryNode(final LibraryLink libraryLink, PackagingArtifact owner) {
    super(owner, libraryLink);
  }

  @NotNull
  protected String getOutputFileName() {
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
      String name = library.getName();
      if (name != null) {
        renderer.setIcon(Icons.LIBRARY_ICON);
        renderer.append(name, getMainAttributes());
        renderer.append(getLibraryTableComment(library), getCommentAttributes());
      }
      else {
        VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
        if (files.length > 0) {
          VirtualFile file = files[0];
          renderer.setIcon(file.getIcon());
          renderer.append(file.getName(), getMainAttributes());
          renderer.append(getLibraryTableComment(library), getCommentAttributes());
        }
        else {
          CellAppearanceUtils.forLibrary(library).customize(renderer);
        }
      }
    }

  }

  private static String getLibraryTableComment(final Library library) {
    LibraryTable libraryTable = library.getTable();
    String displayName;
    if (libraryTable != null) {
      displayName = libraryTable.getPresentation().getDisplayName(false);
    }
    else {
      Module module = ((LibraryImpl)library).getModule();
      String tableName = PackagingEditorUtil.getLibraryTableDisplayName(library);
      displayName = module != null ? "'" + module.getName() + "' " + tableName : tableName;
    }
    return " (" + displayName + ")";
  }

  public boolean canNavigate() {
    return true;
  }
}
