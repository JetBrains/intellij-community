/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class FacetEditorContextBase extends UserDataHolderBase implements FacetEditorContext {
  private FacetsProvider myFacetsProvider;
  @Nullable private final FacetEditorContext myParentContext;
  private ModulesProvider myModulesProvider;
  private UserDataHolder mySharedModuleData;

  public FacetEditorContextBase(final @Nullable FacetEditorContext parentContext, final @Nullable FacetsProvider facetsProvider, final @NotNull ModulesProvider modulesProvider,
                                final UserDataHolder sharedModuleData) {
    mySharedModuleData = sharedModuleData;
    myParentContext = parentContext;
    myModulesProvider = modulesProvider;
    myFacetsProvider = facetsProvider != null ? facetsProvider : DefaultFacetsProvider.INSTANCE;
  }

  public Library[] getLibraries() {
    final Project project = getProject();
    if (project == null) {
      return new Library[0];
    }

    return LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraries();
  }

  @Nullable
  public Library findLibrary(@NotNull String name) {
    for (Library library : getLibraries()) {
      if (name.equals(library.getName())) {
        return library;
      }
    }
    return null;
  }

  //todo[nik] pull up to open API?
  public UserDataHolder getSharedModuleData() {
    return mySharedModuleData;
  }

  @Nullable
  public <T> T getUserData(final Key<T> key) {
    T t = super.getUserData(key);
    if (t == null && myParentContext != null) {
      t = myParentContext.getUserData(key);
    }
    return t;
  }

  @NotNull
  public FacetsProvider getFacetsProvider() {
    return myFacetsProvider;
  }

  @NotNull
  public ModulesProvider getModulesProvider() {
    return myModulesProvider;
  }
}
