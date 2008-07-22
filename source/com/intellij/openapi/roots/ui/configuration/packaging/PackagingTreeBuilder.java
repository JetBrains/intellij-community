package com.intellij.openapi.roots.ui.configuration.packaging;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.deployment.ContainerElement;
import com.intellij.openapi.deployment.LibraryLink;
import com.intellij.openapi.deployment.PackagingMethod;
import com.intellij.openapi.deployment.ModuleLink;
import com.intellij.openapi.diagnostic.Logger;

import java.util.List;
import java.util.Collections;

/**
 * @author nik
 */
public abstract class PackagingTreeBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.packaging.PackagingTreeBuilder");

  public abstract PackagingArtifact createRootArtifact();

  @NotNull
  public List<? extends PackagingTreeNode> createNodes(@NotNull PackagingArtifactNode artifactRoot, @NotNull ContainerElement element,
                                                       final PackagingArtifact owner, final PackagingTreeParameters parameters) {
    if (element instanceof LibraryLink) {
      return Collections.singletonList(createLibraryNode((LibraryLink)element, artifactRoot, owner));
    }
    if (element instanceof ModuleLink) {
      return Collections.singletonList(createModuleNode((ModuleLink)element, artifactRoot, owner));
    }
    LOG.error("unknown element: " + element.getClass());
    return Collections.emptyList();
  }

  @NotNull
  private static PackagingTreeNode createLibraryNode(final LibraryLink libraryLink, final PackagingArtifactNode root, final PackagingArtifact owner) {
    PackagingTreeNode parent = getOrCreateParentNode(libraryLink, root, owner);
    return PackagingTreeNodeFactory.createLibraryNode(libraryLink, parent, owner);
  }
                                                                                                                
  protected static PackagingTreeNode getOrCreateParentNode(final ContainerElement element, final PackagingArtifactNode root, final PackagingArtifact owner) {
    PackagingMethod method = element.getPackagingMethod();
    PackagingTreeNode parent;
    String path = element.getURI();
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

  @NotNull
  private static PackagingTreeNode createModuleNode(ModuleLink moduleLink, PackagingArtifactNode root, final PackagingArtifact owner) {
    PackagingMethod method = moduleLink.getPackagingMethod();
    if (method == PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST || method == PackagingMethod.JAR_AND_COPY_FILE) {
      String path = moduleLink.getURI();
      int i = path.lastIndexOf('/');
      String jarName = path.substring(i+1);
      PackagingTreeNode parent = root;
      if (i != -1) {
        parent = PackagingTreeNodeFactory.getOrCreateDirectoryNode(path.substring(0, i), root, owner);
      }
      return PackagingTreeNodeFactory.createPackedModuleOutputNode(moduleLink, jarName, parent, owner);
    }
    PackagingTreeNode parent = getOrCreateParentNode(moduleLink, root, owner);
    return PackagingTreeNodeFactory.createModuleOutputNode(moduleLink, parent, owner);
  }
}


