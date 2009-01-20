package com.intellij.xml.index;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public enum ResourceRelevance {

  MAPPED,
  SOURCE,
  LIBRARY,
  STANDARD,
  NONE;

  public static ResourceRelevance getRelevance(VirtualFile file, Module module, ProjectFileIndex fileIndex) {
    Module moduleForFile = fileIndex.getModuleForFile(file);
    if (moduleForFile != null) { // in module content
      return module.equals(moduleForFile) ? SOURCE : NONE;
    }
    if (fileIndex.isInLibraryClasses(file)) {
      List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(file);
      if (orderEntries.isEmpty()) {
        return NONE;
      }
      for (OrderEntry orderEntry : orderEntries) {
        Module ownerModule = orderEntry.getOwnerModule();
        if (ownerModule != null) {
          if (ownerModule.equals(module)) {
            return LIBRARY;
          }
        }
      }
    }
    if (((ExternalResourceManagerImpl)ExternalResourceManager.getInstance()).isUserResource(file)) {
      return MAPPED;
    }
    if (((ExternalResourceManagerImpl)ExternalResourceManager.getInstance()).isStandardResource(file)) {
      return STANDARD;
    }
    return NONE;
  }
}
