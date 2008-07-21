package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.deployment.LibraryLink;
import com.intellij.openapi.deployment.ModuleLink;
import org.jetbrains.annotations.NotNull;

import java.util.StringTokenizer;

/**
 * @author nik
 */
public class PackagingTreeNodeFactory {
  private PackagingTreeNodeFactory() {
  }

  @NotNull
  public static PackagingTreeNode getOrCreateDirectoryNode(@NotNull String path, @NotNull PackagingTreeNode root, PackagingArtifact owner) {
    StringTokenizer tokenizer = new StringTokenizer(path, "/");
    PackagingTreeNode current = root;
    while (tokenizer.hasMoreTokens()) {
      String name = tokenizer.nextToken();
      if (name.length() == 0 || name.equals(".")) continue;

      PackagingTreeNode child = current.findChildByName(name);
      if (child == null) {
        child = new DirectoryNode(name, owner);
        current.add(child);
      }
      current = child;
    }
    return current;
  }

  @NotNull
  public static PackagingTreeNode getOrCreateArchiveNode(@NotNull String path, @NotNull PackagingTreeNode root, PackagingArtifact owner) {
    int i = path.lastIndexOf('/');
    String archiveName = path.substring(i+1);
    PackagingTreeNode parent = i != -1 ? getOrCreateDirectoryNode(path.substring(0, i), root, owner) : root;
    PackagingTreeNode child = parent.findChildByName(archiveName);
    if (child == null) {
      child = new ArchiveNode(archiveName, owner);
      parent.add(child);
    }
    return child;
  }

  @NotNull
  public static PackagingTreeNode createLibraryNode(@NotNull LibraryLink libraryLink, @NotNull PackagingTreeNode parent, final PackagingArtifact owner) {
    for (PackagingTreeNode child : parent.getChildren()) {
      if (child instanceof LibraryNode && ((LibraryNode)child).getLibraryLink().equals(libraryLink)) {
        return child;
      }
    }
    LibraryNode node = new LibraryNode(libraryLink, owner);
    parent.add(node);
    return node;
  }

  @NotNull
  public static PackagingTreeNode createPackedModuleOutputNode(@NotNull ModuleLink moduleLink, String jarName, @NotNull PackagingTreeNode parent, final PackagingArtifact owner) {
    for (PackagingTreeNode child : parent.getChildren()) {
      if (child instanceof PackedModuleOutputNode && ((PackedModuleOutputNode)child).getModuleLink().equals(moduleLink)) {
        return child;
      }
    }
    PackedModuleOutputNode node = new PackedModuleOutputNode(moduleLink, jarName, owner);
    parent.add(node);
    return node;
  }

  @NotNull
  public static PackagingTreeNode createModuleOutputNode(@NotNull ModuleLink moduleLink, @NotNull PackagingTreeNode parent, final PackagingArtifact owner) {
    for (PackagingTreeNode child : parent.getChildren()) {
      if (child instanceof ModuleOutputNode && ((ModuleOutputNode)child).getModuleLink().equals(moduleLink)) {
        return child;
      }
    }
    ModuleOutputNode node = new ModuleOutputNode(moduleLink, owner);
    parent.add(node);
    return node;
  }

  public static PackagingArtifactNode createArtifactNode(@NotNull PackagingArtifact artifact, @NotNull PackagingTreeNode parent,
                                                         PackagingArtifact owner) {
    PackagingArtifactNode node = new PackagingArtifactNode(artifact, owner);
    parent.add(node);
    return node;
  }
}
