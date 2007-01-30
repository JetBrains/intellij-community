/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.facet.impl.ui.libraries.FacetLibrariesConfigurationImpl;
import com.intellij.facet.impl.ui.libraries.FacetLibrariesEditorImpl;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorsFactory;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.libraries.FacetLibrariesConfiguration;
import com.intellij.facet.ui.libraries.FacetLibrariesEditor;
import com.intellij.facet.ui.libraries.FacetLibrariesEditorDescription;
import com.intellij.facet.ui.libraries.LibraryInfo;

/**
 * @author nik
 */
public class FacetEditorsFactoryImpl extends FacetEditorsFactory {
  public FacetLibrariesEditor createLibrariesEditor(final FacetEditorContext editorContext, final FacetValidatorsManager validatorsManager,
                                                    final FacetLibrariesConfiguration configuration,
                                                    FacetLibrariesEditorDescription editorDescription, final LibraryInfo[] libraryInfos) {
    return new FacetLibrariesEditorImpl(editorContext, validatorsManager, configuration, editorDescription, libraryInfos);
  }

  public FacetLibrariesConfiguration createLibrariesConfiguration() {
    return new FacetLibrariesConfigurationImpl();
  }
}
