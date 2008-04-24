package com.intellij.xml.index;

import com.intellij.javaee.ExternalResourceManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.IndexedRootsProvider;

import java.net.URL;
import java.util.Collections;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class ExternalResourcesRootsProvider implements IndexedRootsProvider {

  private VirtualFile getStandardSchemas() {
    final URL resource = getClass().getResource(ExternalResourceManagerImpl.STANDARD_SCHEMAS);
    return VfsUtil.findFileByURL(resource);
  }

  public Set<VirtualFile> getRootsToIndex(final Project project) {
    final VirtualFile file = getStandardSchemas();
    return Collections.singleton(file);
  }
}
