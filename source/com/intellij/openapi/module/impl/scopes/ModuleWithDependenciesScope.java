/*
 * Copyright (c) 2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiBundle;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class ModuleWithDependenciesScope extends GlobalSearchScope {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope");

  private final Module myModule;
  private final boolean myIncludeLibraries;
  private final boolean myIncludeOtherModules;
  private final boolean myIncludeTests;

  private final ModuleFileIndex myFileIndex;
  private final Set<Module> myModules;

  public ModuleWithDependenciesScope(Module module, boolean includeLibraries, boolean includeOtherModules, boolean includeTests) {
    myModule = module;
    myIncludeLibraries = includeLibraries;
    myIncludeOtherModules = includeOtherModules;
    myIncludeTests = includeTests;

    myFileIndex = ModuleRootManager.getInstance(myModule).getFileIndex();

    if (myIncludeOtherModules) {
      myModules = new HashSet<Module>();
      myModules.add(myModule);
      Module[] dependencies = ModuleRootManager.getInstance(myModule).getDependencies();
      myModules.addAll(Arrays.asList(dependencies));
      for (Module dependency : dependencies) {
        addExportedModules(dependency);
      }
    }
    else {
      myModules = null;
    }
  }

  private void addExportedModules(Module module) {
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (!orderEntry.isValid()) {
        continue;
      }
      if (orderEntry instanceof ModuleOrderEntry && ((ModuleOrderEntry)orderEntry).isExported()) {
        Module exportedModule = ((ModuleOrderEntry)orderEntry).getModule();
        if (!myModules.contains(exportedModule)) { //could be true in case of circular dependencies
          myModules.add(exportedModule);
          addExportedModules(exportedModule);
        }
      }
    }
  }

  public boolean contains(VirtualFile file) {
    if (!myIncludeTests && myFileIndex.isInTestSourceContent(file)) return false;

    final List<OrderEntry> entries = myFileIndex.getOrderEntriesForFile(file);
    for (OrderEntry orderEntry : entries) {
      if (myIncludeLibraries) {
        if (myIncludeOtherModules) {
          return true;
        }
        else {
          if (!(orderEntry instanceof ModuleOrderEntry)) return true;
        }
      }
      else {
        if (myIncludeOtherModules) {
          if (orderEntry instanceof ModuleSourceOrderEntry || orderEntry instanceof ModuleOrderEntry) return true;
        }
        else {
          if (orderEntry instanceof ModuleSourceOrderEntry) return true;
        }
      }
    }

    return false;
  }

  public int compare(VirtualFile file1, VirtualFile file2) {
    OrderEntry orderEntry1 = myFileIndex.getOrderEntryForFile(file1);
    LOG.assertTrue(orderEntry1 != null);
    OrderEntry orderEntry2 = myFileIndex.getOrderEntryForFile(file2);
    LOG.assertTrue(orderEntry2 != null);
    return orderEntry2.compareTo(orderEntry1);
  }

  public boolean isSearchInModuleContent(Module aModule) {
    if (myIncludeOtherModules) {
      return myModules.contains(aModule);
    }
    else {
      return aModule == myModule;
    }
  }

  public boolean isSearchInLibraries() {
    return myIncludeLibraries;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleWithDependenciesScope)) return false;

    final ModuleWithDependenciesScope moduleWithDependenciesScope = (ModuleWithDependenciesScope)o;

    if (!myModule.equals(moduleWithDependenciesScope.myModule)) return false;
    if (myIncludeLibraries != moduleWithDependenciesScope.myIncludeLibraries) return false;
    if (myIncludeOtherModules != moduleWithDependenciesScope.myIncludeOtherModules) return false;
    if (myIncludeTests != moduleWithDependenciesScope.myIncludeTests) return false;

    return true;
  }

  public int hashCode() {
    return myModule.hashCode();
  }

  @NonNls
  public String toString() {
    return "Module with dependencies:" + myModule.getName() +
           " include libraries:" + myIncludeLibraries +
           " include other modules:" + myIncludeOtherModules +
           " include tests:" + myIncludeTests;
  }

  @Override
  public String getDisplayName() {
    return PsiBundle.message("psi.search.scope.module", myModule.getName());
  }
}
