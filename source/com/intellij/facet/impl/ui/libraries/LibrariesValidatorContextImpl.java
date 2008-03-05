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
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;

/**
 * @author nik
 */
public class LibrariesValidatorContextImpl implements LibrariesValidatorContext {
  private Module myModule;
  private LibrariesContainer myLibrariesContainer;

  public LibrariesValidatorContextImpl(final @NotNull Module module) {
    myModule = module;
    myLibrariesContainer = LibrariesContainerFactory.createContainer(module);
  }

  @Nullable
  public ModuleRootModel getRootModel() {
    return ModuleRootManager.getInstance(myModule);
  }

  @Nullable
  public ModifiableRootModel getModifiableRootModel() {
    return null;
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

  public LibrariesContainer getLibrariesContainer() {
    return myLibrariesContainer;
  }

}
