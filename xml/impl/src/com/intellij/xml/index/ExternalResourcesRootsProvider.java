package com.intellij.xml.index;

import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.javaee.ExternalResourceManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.indexing.IndexedRootsProvider;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class ExternalResourcesRootsProvider implements IndexedRootsProvider {

  @Nullable
  public static VirtualFile getStandardSchemas() {
    final URL resource = ExternalResourcesRootsProvider.class.getResource(ExternalResourceManagerImpl.STANDARD_SCHEMAS);
    return resource == null ? null : VfsUtil.findFileByURL(resource);
  }

  public Set<VirtualFile> getRootsToIndex(final Project project) {
    final VirtualFile standardSchemas = getStandardSchemas();
    String path = FetchExtResourceAction.getExternalResourcesPath();
    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    VirtualFile extResources = localFileSystem.findFileByPath(path);
    HashSet<VirtualFile> roots = new HashSet<VirtualFile>(2);
    roots.add(standardSchemas);
    if (extResources != null) {
      roots.add(extResources);
    }
    return roots;
  }
}
