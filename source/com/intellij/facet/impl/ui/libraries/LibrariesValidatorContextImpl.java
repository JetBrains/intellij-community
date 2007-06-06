/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui.libraries;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.facet.impl.ui.FacetEditorContextBase;

/**
 * @author nik
 */
public class LibrariesValidatorContextImpl implements LibrariesValidatorContext {
  private Module myModule;

  public LibrariesValidatorContextImpl(final Module module) {
    myModule = module;
  }

  @Nullable
  public ModuleRootModel getRootModel() {
    return ModuleRootManager.getInstance(myModule);
  }

  @Nullable
  public ModifiableRootModel getModifiableRootModel() {
    return null;
  }

  @NotNull
  public Library[] getLibraries() {
    return getProjectLibraryTable().getLibraries();
  }

  private LibraryTable getProjectLibraryTable() {
    return ProjectLibraryTable.getInstance(myModule.getProject());
  }

  @NotNull
  public ModulesProvider getModulesProvider() {
    return new DefaultModulesProvider(myModule.getProject());
  }

  @Nullable
  public Project getProject() {
    return myModule.getProject();
  }

  public Library createProjectLibrary(String name, VirtualFile[] roots) {
    return FacetEditorContextBase.createLibraryInTable(name, roots, getProjectLibraryTable());
  }
}
