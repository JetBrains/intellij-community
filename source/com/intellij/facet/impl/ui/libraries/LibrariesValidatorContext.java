/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui.libraries;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface LibrariesValidatorContext {
  @Nullable
  ModuleRootModel getRootModel();

  @Nullable
  ModifiableRootModel getModifiableRootModel();

  @NotNull
  Library[] getLibraries();

  @NotNull
  ModulesProvider getModulesProvider();

  @Nullable
  Project getProject();

  Library createProjectLibrary(String name, VirtualFile[] roots);

  VirtualFile[] getFiles(final Library library, final OrderRootType rootType);
}
