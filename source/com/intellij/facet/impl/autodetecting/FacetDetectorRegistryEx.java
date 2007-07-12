/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.autodetecting.FacetDetectorRegistry;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.util.Condition;
import com.intellij.patterns.impl.VirtualFilePattern;
import com.intellij.patterns.impl.PsiFilePattern;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class FacetDetectorRegistryEx<C extends FacetConfiguration> implements FacetDetectorRegistry<C> {
  private final @Nullable FacetDetectorForWizardRegistry<C> myForWizardDelegate;
  private final @Nullable FacetOnTheFlyDetectorRegistry<C> myOnTheFlyDelegate;

  public FacetDetectorRegistryEx(final @Nullable FacetDetectorForWizardRegistry<C> forWizardDelegate, final @Nullable FacetOnTheFlyDetectorRegistry<C> onTheFlyDelegate) {
    myForWizardDelegate = forWizardDelegate;
    myOnTheFlyDelegate = onTheFlyDelegate;
  }

  public void registerUniversalDetector(@NotNull final FileType fileType, @NotNull final VirtualFilePattern virtualFilePattern,
                       @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
    registerUniversalDetector(fileType, new MyPatternFilter(virtualFilePattern), facetDetector);
  }

  public void registerDetectorForWizard(@NotNull final FileType fileType, @NotNull final VirtualFilePattern virtualFilePattern,
                       @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
    registerDetectorForWizard(fileType, new MyPatternFilter(virtualFilePattern), facetDetector);
  }

  public <U extends FacetConfiguration> void registerSubFacetDetectorForWizard(@NotNull final FileType fileType, @NotNull final VirtualFilePattern virtualFilePattern,
                       @NotNull final FacetDetector<VirtualFile, C> facetDetector,
                       @NotNull final UnderlyingFacetSelector<VirtualFile, U> underlyingFacetSelector) {
    if (myForWizardDelegate != null) {
      myForWizardDelegate.register(fileType, new MyPatternFilter(virtualFilePattern), facetDetector, underlyingFacetSelector);
    }
  }

  public void registerOnTheFlyDetector(@NotNull final FileType fileType, @NotNull final VirtualFilePattern virtualFilePattern,
                       @NotNull final PsiFilePattern<? extends PsiFile, ?> psiFilePattern,
                       @NotNull final FacetDetector<PsiFile, C> facetDetector) {
    registerOnTheFlyDetector(fileType, new MyPatternFilter(virtualFilePattern), new Condition<PsiFile>() {
      public boolean value(final PsiFile psiFile) {
        return psiFilePattern.accepts(psiFile);
      }
    }, facetDetector);
  }


  public void registerUniversalDetector(@NotNull final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter, @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
    if (myForWizardDelegate != null) {
      myForWizardDelegate.register(fileType, virtualFileFilter, facetDetector);
    }
    if (myOnTheFlyDelegate != null) {
      myOnTheFlyDelegate.register(fileType, virtualFileFilter, facetDetector);
    }
  }

  public void registerDetectorForWizard(@NotNull final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter, @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
    if (myForWizardDelegate != null) {
      myForWizardDelegate.register(fileType, virtualFileFilter, facetDetector);
    }
  }

  public void registerOnTheFlyDetector(@NotNull final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter, @NotNull final Condition<PsiFile> psiFileFilter,
                                       @NotNull final FacetDetector<PsiFile, C> facetDetector) {
    if (myOnTheFlyDelegate != null) {
      myOnTheFlyDelegate.register(fileType, virtualFileFilter, psiFileFilter, facetDetector);
    }
  }

  private static class MyPatternFilter implements VirtualFileFilter {
    private final VirtualFilePattern myVirtualFilePattern;

    public MyPatternFilter(final VirtualFilePattern virtualFilePattern) {
      myVirtualFilePattern = virtualFilePattern;
    }

    public boolean accept(final VirtualFile file) {
      return myVirtualFilePattern.accepts(file);
    }
  }
}
