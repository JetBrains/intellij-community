package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.deployment.ContainerElement;
import com.intellij.openapi.deployment.LibraryLink;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.util.CellAppearanceUtils;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
class LibraryNode extends PackagingTreeNode {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.packaging.LibraryNode");
  private LibraryLink myLibraryLink;

  LibraryNode(final LibraryLink libraryLink, PackagingArtifact owner) {
    super(owner);
    myLibraryLink = libraryLink;
  }

  public LibraryLink getLibraryLink() {
    return myLibraryLink;
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
        LibraryTable libraryTable = library.getTable();
        if (libraryTable != null) {
          renderer.append(" (" + libraryTable.getPresentation().getDisplayName(false) + ")", getCommentAttributes());
        }
      }
      else {
        VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
        if (files.length > 0) {
          VirtualFile file = files[0];
          renderer.setIcon(file.getIcon());
          renderer.append(file.getName(), getMainAttributes());
        }
        else {
          CellAppearanceUtils.forLibrary(library).customize(renderer);
        }
      }
    }

    if (belongsToIncludedArtifact()) {
      PackagingArtifact owner = getOwner();
      LOG.assertTrue(owner != null);
      if (library != null) {
        renderer.append(" " + ProjectBundle.message("node.text.packaging.included.from.0", owner.getDisplayName()), getCommentAttributes());
      }
    }
  }

  public boolean canNavigate() {
    return true;
  }

  public void navigate(final ModuleStructureConfigurable configurable) {
    Module parentModule = myLibraryLink.getParentModule();
    if (parentModule != null) {
      ModulesConfigurator modulesConfigurator = configurable.getContext().myModulesConfigurator;
      ModuleRootModel rootModel = modulesConfigurator.getRootModel(parentModule);
      LibraryOrderEntry orderEntry = OrderEntryUtil.findLibraryOrderEntry(rootModel, myLibraryLink.getLibrary(), true, modulesConfigurator);
      configurable.selectOrderEntry(orderEntry != null ? orderEntry.getOwnerModule() : parentModule, orderEntry);
    }
  }

  public Object getSourceObject() {
    return myLibraryLink.getLibrary();
  }

  @Override
  public ContainerElement getContainerElement() {
    return myLibraryLink;
  }
}
