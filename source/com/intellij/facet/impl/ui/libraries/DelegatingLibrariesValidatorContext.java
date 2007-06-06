/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DelegatingLibrariesValidatorContext implements LibrariesValidatorContext {
  private FacetEditorContext myDelegate;

  public DelegatingLibrariesValidatorContext(final @NotNull FacetEditorContext delegate) {
    myDelegate = delegate;
  }

  @Nullable
  public Project getProject() {
    return myDelegate.getProject();
  }

  @NotNull
  public ModulesProvider getModulesProvider() {
    return myDelegate.getModulesProvider();
  }

  @Nullable
  public ModifiableRootModel getModifiableRootModel() {
    return myDelegate.getModifiableRootModel();
  }

  @Nullable
  public ModuleRootModel getRootModel() {
    return myDelegate.getRootModel();
  }

  @NotNull
  public Library[] getLibraries() {
    return myDelegate.getLibraries();
  }

  public Library createProjectLibrary(final String name, final VirtualFile[] roots) {
    return myDelegate.createProjectLibrary(name, roots);
  }
}
