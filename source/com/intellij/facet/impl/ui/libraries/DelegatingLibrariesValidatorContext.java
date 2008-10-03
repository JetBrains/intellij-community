/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.impl.ui.FacetEditorContextBase;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.module.Module;
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

  @NotNull
  public Module getModule() {
    return myDelegate.getModule();
  }

  public LibrariesContainer getLibrariesContainer() {
    return ((FacetEditorContextBase)myDelegate).getContainer();
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

}
