/*
 * Copyright (c) 2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author max
 */
public class ModuleRuntimeClasspathScope extends GlobalSearchScope {
  private final ProjectFileIndex myIndex;
  private boolean myIncludeTests;
  private LinkedHashSet<VirtualFile> myEntries = new LinkedHashSet<VirtualFile>();
  private Module myModule;

  public ModuleRuntimeClasspathScope(final Module module, boolean includeTests) {
    myModule = module;
    myIndex = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
    myIncludeTests = includeTests;
    buildEntries(module);
  }

  public int hashCode() {
    return myModule.hashCode();
  }

  public boolean equals(Object object) {
    if (object == this) return true;
    if (object.getClass() != ModuleRuntimeClasspathScope.class) return false;

    final ModuleRuntimeClasspathScope that = ((ModuleRuntimeClasspathScope)object);
    return that.myModule == myModule && that.myIncludeTests == myIncludeTests;
  }

  private void buildEntries(final Module module) {
    final Set<Module> processedModules = new HashSet<Module>();
    processedModules.add(module);

    ModuleRootManager.getInstance(module).processOrder(new RootPolicy<LinkedHashSet<VirtualFile>>() {
      private boolean myJDKProcessed = false;

      public LinkedHashSet<VirtualFile> visitModuleSourceOrderEntry(final ModuleSourceOrderEntry moduleSourceOrderEntry,
                                                                    final LinkedHashSet<VirtualFile> value) {
        value.addAll(Arrays.asList(moduleSourceOrderEntry.getFiles(OrderRootType.SOURCES)));
        return value;
      }

      public LinkedHashSet<VirtualFile> visitLibraryOrderEntry(final LibraryOrderEntry libraryOrderEntry,
                                                               final LinkedHashSet<VirtualFile> value) {
        value.addAll(Arrays.asList(libraryOrderEntry.getFiles(OrderRootType.CLASSES)));
        return value;
      }

      public LinkedHashSet<VirtualFile> visitModuleOrderEntry(final ModuleOrderEntry moduleOrderEntry,
                                                              final LinkedHashSet<VirtualFile> value) {
        final Module depModule = moduleOrderEntry.getModule();
        if (depModule != null && !processedModules.contains(depModule)) {
          processedModules.add(depModule);
          ModuleRootManager.getInstance(depModule).processOrder(this, value);
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
    if (!myIncludeTests && myIndex.isInTestSourceContent(file)) return false;
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
    return true;
  }

  public boolean isSearchInLibraries() {
    return true;
  }

  public String getDisplayName() {
    return PsiBundle.message("runtime.scope.display.name", myModule.getName());
  }
}
