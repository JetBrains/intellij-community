package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.deployment.ContainerElement;
import com.intellij.openapi.deployment.LibraryLink;
import com.intellij.openapi.deployment.ModuleLink;
import com.intellij.openapi.deployment.PackagingMethod;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class PackagingTreeBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.packaging.PackagingTreeBuilder");

  public abstract PackagingArtifact createRootArtifact();

  @NotNull
  public PackagingTreeParameters getDefaultParameters() {
    return new PackagingTreeParameters(false, false);
  }

  public void updateParameters(final @NotNull PackagingTreeParameters treeParameters) {
  }

  @NotNull
  public List<? extends PackagingTreeNode> createNodes(@NotNull PackagingArtifactNode artifactRoot, @NotNull ContainerElement element,
                                                       final PackagingArtifact owner, final PackagingTreeParameters parameters) {
    if (element instanceof LibraryLink) {
      return createLibraryNodes((LibraryLink)element, artifactRoot, owner, parameters);
    }
    if (element instanceof ModuleLink) {
      return Collections.singletonList(createModuleNode((ModuleLink)element, artifactRoot, owner));
    }
    LOG.error("unknown element: " + element.getClass());
    return Collections.emptyList();
  }

  @NotNull
  private static List<PackagingTreeNode> createLibraryNodes(final LibraryLink libraryLink, final PackagingArtifactNode root, final PackagingArtifact owner,
                                                            final PackagingTreeParameters parameters) {
    PackagingTreeNode parent = getOrCreateParentNode(libraryLink, root, owner);
    if (parameters.isShowLibraryFiles()) {
      Library library = libraryLink.getLibrary();
      if (library != null) {
        VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
        List<PackagingTreeNode> nodes = new ArrayList<PackagingTreeNode>();
        for (VirtualFile file : files) {
          nodes.add(PackagingTreeNodeFactory.createLibraryFileNode(file, library, libraryLink, parent, owner));
        }
        return nodes;
      }
    }
    return Collections.singletonList(PackagingTreeNodeFactory.createLibraryNode(libraryLink, parent, owner));
  }
                                                                                                                
  protected static PackagingTreeNode getOrCreateParentNode(final ContainerElement element, final PackagingArtifactNode root, final PackagingArtifact owner) {
    PackagingMethod method = element.getPackagingMethod();
    PackagingTreeNode parent;
    String path = element.getURI();

    path = fixPath(element, method, path);

    if (method == PackagingMethod.JAR_AND_COPY_FILE) {
      parent = PackagingTreeNodeFactory.getOrCreateArchiveNode(path, root, owner);
    }
    else if (method == PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST) {
      parent = PackagingTreeNodeFactory.getOrCreateArchiveNode(path, root.getParent(), owner);
    }
    else if (method == PackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST) {
      parent = PackagingTreeNodeFactory.getOrCreateDirectoryNode(path, root.getParent(), owner);
    }
    else {  
      parent = PackagingTreeNodeFactory.getOrCreateDirectoryNode(path, root, owner);
    }
    return parent;
  }

  private static String fixPath(final ContainerElement element, final PackagingMethod method, final String path) {
    if (element instanceof LibraryLink) {
      final LibraryLink libraryLink = (LibraryLink)element;
      @NonNls String jarSuffix = ".jar";
      if ((method == PackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST || method == PackagingMethod.COPY_FILES)
          && libraryLink.getUrls().size() == 1 && LibraryLink.MODULE_LEVEL.equals(libraryLink.getLevel()) && path.endsWith(jarSuffix)) {
        int index = path.lastIndexOf('/');
        if (index >= 0) {
          return path.substring(0, index);
        }
        else {
          return "";
        }
      }
    }
    return path;
  }

  @NotNull
  private static PackagingTreeNode createModuleNode(ModuleLink moduleLink, PackagingArtifactNode root, final PackagingArtifact owner) {
    PackagingMethod method = moduleLink.getPackagingMethod();
    if (method == PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST || method == PackagingMethod.JAR_AND_COPY_FILE) {
      String path = moduleLink.getURI();
      int i = path.lastIndexOf('/');
      String jarName = path.substring(i+1);
      PackagingTreeNode parent = root;
      if (i != -1) {
        String parentPath = path.substring(0, i);
        if (method == PackagingMethod.JAR_AND_COPY_FILE) {
          parent = PackagingTreeNodeFactory.getOrCreateDirectoryNode(parentPath, root, owner);
        }
        else {
          parent = PackagingTreeNodeFactory.getOrCreateDirectoryNode(parentPath, root.getParent(), owner);
        }
      }
      return PackagingTreeNodeFactory.createPackedModuleOutputNode(moduleLink, jarName, parent, owner);
    }
    PackagingTreeNode parent = getOrCreateParentNode(moduleLink, root, owner);
    return PackagingTreeNodeFactory.createModuleOutputNode(moduleLink, parent, owner);
  }
}


