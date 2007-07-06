/*
 * Copyright (c) 2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class ModuleWithDependentsScope extends GlobalSearchScope {
  private final Module myModule;
  private final boolean myOnlyTests;

  private final ProjectFileIndex myProjectFileIndex;
  private final Set<Module> myModules;

  public ModuleWithDependentsScope(Module module, boolean onlyTests) {
    myModule = module;
    myOnlyTests = onlyTests;

    myProjectFileIndex = ProjectRootManager.getInstance(myModule.getProject()).getFileIndex();

    myModules = new HashSet<Module>();
    myModules.add(myModule);
    List<Module> dependents = ModuleManager.getInstance(myModule.getProject()).getModuleDependentModules(myModule);
    for (Module dependent : dependents) {
      addExportedModules(dependent, myModule);
    }
    myModules.addAll(dependents); //important to add after the previous loop
  }

  private void addExportedModules(Module dependentModule, Module module) {

    OrderEntry[] orderEntries = ModuleRootManager.getInstance(dependentModule).getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof ModuleOrderEntry && module.equals(((ModuleOrderEntry)orderEntry).getModule())) {
        if (((ModuleOrderEntry)orderEntry).isExported()) {
          List<Module> nextLevelModules = ModuleManager.getInstance(myModule.getProject()).getModuleDependentModules(dependentModule);
          for (Module nextLevelModule : nextLevelModules) {
            if (!myModules.contains(nextLevelModule)) { //Could be true in case of circular dependencies
              myModules.add(nextLevelModule);
              addExportedModules(nextLevelModule, dependentModule);
            }
          }
        }
      }
    }
  }

  public boolean contains(VirtualFile file) {
    Module moduleOfFile = myProjectFileIndex.getModuleForFile(file);
    if (moduleOfFile == null) return false;
    if (!myModules.contains(moduleOfFile)) return false;
    if (myOnlyTests && !myProjectFileIndex.isInTestSourceContent(file)) return false;
    return true;
  }

  public int compare(VirtualFile file1, VirtualFile file2) {
    return 0;
  }

  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return myModules.contains(aModule);
  }

  public boolean isSearchInLibraries() {
    return false;
  }

  @NonNls
  public String toString() {
    return "Module with dependents:" + myModule.getName();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleWithDependentsScope)) return false;

    final ModuleWithDependentsScope moduleWithDependentsScope = (ModuleWithDependentsScope)o;

    if (myOnlyTests != moduleWithDependentsScope.myOnlyTests) return false;
    if (!myModule.equals(moduleWithDependentsScope.myModule)) return false;

    return true;
  }

  public int hashCode() {
    return myModule.hashCode();
  }
}
