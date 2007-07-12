/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface FacetDetectorForWizardRegistry<C extends FacetConfiguration> {
  void register(@NotNull final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter, 
                @NotNull final FacetDetector<VirtualFile, C> facetDetector);

  <U extends FacetConfiguration> void register(final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter, 
                                               final FacetDetector<VirtualFile, C> facetDetector,
                                               final UnderlyingFacetSelector<VirtualFile, U> underlyingFacetSelector);
}
