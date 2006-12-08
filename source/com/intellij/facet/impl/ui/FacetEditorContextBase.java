/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class FacetEditorContextBase implements FacetEditorContext {
  private @Nullable Project myProject;

  public FacetEditorContextBase(final @Nullable Project project) {
    myProject = project;
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  public Library[] getLibraries() {
    if (myProject == null) {
      return new Library[0];
    }

    return LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraries();
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
}
