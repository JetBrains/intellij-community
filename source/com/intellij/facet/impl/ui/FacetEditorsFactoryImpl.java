/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.facet.impl.ui.libraries.*;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorsFactory;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.libraries.*;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class FacetEditorsFactoryImpl extends FacetEditorsFactory {
  public FacetLibrariesEditor createLibrariesEditor(final FacetEditorContext editorContext, final FacetValidatorsManager validatorsManager,
                                                    final FacetLibrariesConfiguration configuration,
                                                    FacetLibrariesEditorDescription editorDescription, final LibraryInfo[] libraryInfos) {
    return new FacetLibrariesEditorImpl(editorContext, validatorsManager, configuration, editorDescription, libraryInfos);
  }

  public FacetLibrariesValidator createLibrariesValidator(@NotNull final LibraryInfo[] libraries, final FacetLibrariesValidatorDescription description,
                                                          final FacetEditorContext context,
                                                          final FacetValidatorsManager validatorsManager) {
    return new FacetLibrariesValidatorImpl(libraries, description, new DelegatingLibrariesValidatorContext(context), validatorsManager);
  }

  public LibrariesValidationComponent createLibrariesValidationComponent(LibraryInfo[] libraryInfos, Module module, 
                                                                         String defaultLibraryName) {
    return new LibrariesValidationComponentImpl(libraryInfos, module, defaultLibraryName);
  }

  public FacetLibrariesConfiguration createLibrariesConfiguration() {
    return new FacetLibrariesConfigurationImpl();
  }
}
