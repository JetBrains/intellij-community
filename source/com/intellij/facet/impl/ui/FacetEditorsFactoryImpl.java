/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.facet.ui.FacetEditorsFactory;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.libraries.*;
import com.intellij.facet.impl.ui.libraries.FacetLibrariesEditorImpl;
import com.intellij.facet.impl.ui.libraries.FacetLibrariesConfigurationImpl;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class FacetEditorsFactoryImpl extends FacetEditorsFactory implements ApplicationComponent {

  @NonNls
  @NotNull
  public String getComponentName() {
    return "FacetEditorsFactory";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public FacetLibrariesEditor createLibrariesEditor(final FacetEditorContext editorContext, final FacetValidatorsManager validatorsManager,
                                                    final FacetLibrariesConfiguration configuration,
                                                    FacetLibrariesEditorDescription editorDescription, final LibraryInfo[] libraryInfos) {
    return new FacetLibrariesEditorImpl(editorContext, validatorsManager, configuration, editorDescription, libraryInfos);
  }

  public FacetLibrariesConfiguration createLibrariesConfiguration() {
    return new FacetLibrariesConfigurationImpl();
  }
}
