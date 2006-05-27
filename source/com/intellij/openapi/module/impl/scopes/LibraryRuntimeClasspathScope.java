/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.*;

/**
 * @author max
 */
public class LibraryRuntimeClasspathScope extends GlobalSearchScope {
  private final ProjectFileIndex myIndex;
  private LinkedHashSet<VirtualFile> myEntries = new LinkedHashSet<VirtualFile>();
  private List<Module> myModules;
  private boolean myJDKProcessed = false;

  public LibraryRuntimeClasspathScope(final Project project, final List<Module> modules) {
    myModules = modules;
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final HashSet<Module> processed = new HashSet<Module>();
    for (Module module : modules) {
      buildEntries(module, processed);
    }
  }

  public int hashCode() {
    return myModules.hashCode();
  }

  public boolean equals(Object object) {
    if (object == this) return true;
    if (object.getClass() != LibraryRuntimeClasspathScope.class) return false;

    final LibraryRuntimeClasspathScope that = ((LibraryRuntimeClasspathScope)object);
    return that.myModules.equals(myModules);
  }

  private void buildEntries(final Module module, final Set<Module> processedModules) {
    if (processedModules.contains(module)) return;

    processedModules.add(module);

    ModuleRootManager.getInstance(module).processOrder(new RootPolicy<LinkedHashSet<VirtualFile>>() {
      public LinkedHashSet<VirtualFile> visitLibraryOrderEntry(final LibraryOrderEntry libraryOrderEntry,
                                                               final LinkedHashSet<VirtualFile> value) {
        value.addAll(Arrays.asList(libraryOrderEntry.getFiles(OrderRootType.CLASSES)));
        return value;
      }

      public LinkedHashSet<VirtualFile> visitModuleOrderEntry(final ModuleOrderEntry moduleOrderEntry,
                                                              final LinkedHashSet<VirtualFile> value) {
        final Module depModule = moduleOrderEntry.getModule();
        if (depModule != null) {
          buildEntries(depModule, processedModules);
        }
        return value;
      }

      public LinkedHashSet<VirtualFile> visitJdkOrderEntry(final JdkOrderEntry jdkOrderEntry, final LinkedHashSet<VirtualFile> value) {
        if (myJDKProcessed) return value;
        myJDKProcessed = true;
        value.addAll(Arrays.asList(jdkOrderEntry.getFiles(OrderRootType.CLASSES)));
        return value;
      }
    }, myEntries);
  }

  public boolean contains(VirtualFile file) {
    return myEntries.contains(getFileRoot(file));
  }

  private VirtualFile getFileRoot(VirtualFile file) {
    if (myIndex.isLibraryClassFile(file)) {
      return myIndex.getClassRootForFile(file);
    }
    if (myIndex.isInContent(file)) {
      return myIndex.getSourceRootForFile(file);
    }
    return null;
  }

  public int compare(VirtualFile file1, VirtualFile file2) {
    final VirtualFile r1 = getFileRoot(file1);
    final VirtualFile r2 = getFileRoot(file2);
    for (VirtualFile root : myEntries) {
      if (r1 == root) return 1;
      if (r2 == root) return -1;
    }
    return 0;
  }

  public boolean isSearchInModuleContent(Module aModule) {
    return false;
  }

  public boolean isSearchInLibraries() {
    return true;
  }
}
