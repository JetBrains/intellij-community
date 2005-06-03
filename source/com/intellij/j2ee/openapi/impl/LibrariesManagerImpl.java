package com.intellij.j2ee.openapi.impl;

import com.intellij.j2ee.LibrariesManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.text.StringTokenizer;

/**
 * author: lesya
 */
public class LibrariesManagerImpl extends LibrariesManager implements ApplicationComponent {
  public String getComponentName() {
    return "LabraryManager";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public boolean isClassAvailableInLibrary(Library library, String fqn) {
    final String[] urls = library.getUrls(OrderRootType.CLASSES);
    return isClassAvailable(urls, fqn);
  }

  public boolean isClassAvailable(final String[] urls, String fqn) {
    for (String url : urls) {
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      if (file == null) continue;
      if (file.getFileSystem() != JarFileSystem.getInstance() && !file.isDirectory()) {
        file = JarFileSystem.getInstance().findFileByPath(file.getPath() + JarFileSystem.JAR_SEPARATOR);
      }
      if (file == null) continue;
      if (findInFile(file, new StringTokenizer(fqn, "."))) return true;
    }
    return false;
  }

  private static boolean findInFile(VirtualFile root, final StringTokenizer filePath) {
    if (!filePath.hasMoreTokens()) return true;
    String name = filePath.nextToken();
    if (!filePath.hasMoreTokens()) {
      name += ".class";
    }
    final VirtualFile child = root.findChild(name);
    if (child != null) return findInFile(child, filePath);
    return false;
  }

}
