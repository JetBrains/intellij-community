/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.*;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.impl.autodetecting.model.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public abstract class FacetDetectorWrapper<S, C extends FacetConfiguration, F extends Facet<C>, U extends FacetConfiguration> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.autodetecting.FacetDetectorWrapper");
  private final AutodetectionFilter myAutodetectionFilter;
  private final VirtualFileFilter myVirtualFileFilter;
  private final FacetDetector<S,C> myFacetDetector;
  private final UnderlyingFacetSelector<VirtualFile, U> myUnderlyingFacetSelector;
  private final ProjectFacetInfoSet myDetectedFacetSet;
  private final FacetType<F,C> myFacetType;

  protected FacetDetectorWrapper(ProjectFacetInfoSet projectFacetSet, FacetType<F, C> facetType, final AutodetectionFilter autodetectionFilter, final VirtualFileFilter virtualFileFilter,
                                 final FacetDetector<S, C> facetDetector,
                                 final UnderlyingFacetSelector<VirtualFile, U> selector) {
    myDetectedFacetSet = projectFacetSet;
    myFacetType = facetType;
    myAutodetectionFilter = autodetectionFilter;
    myVirtualFileFilter = virtualFileFilter;
    myFacetDetector = facetDetector;
    myUnderlyingFacetSelector = selector;
  }

  public VirtualFileFilter getVirtualFileFilter() {
    return myVirtualFileFilter;
  }

  public FacetType<?, C> getFacetType() {
    return myFacetType;
  }

  @Nullable
  protected FacetInfo2<Module> detectFacet(@NotNull final Module module, VirtualFile virtualFile, S source) {
    String url = virtualFile.getUrl();
    if (!myAutodetectionFilter.isAutodetectionEnabled(module, myFacetType, url)) {
      LOG.debug("Autodetection disabled for " + myFacetType.getPresentableName() + " facets in module " + module.getName());
      return null;
    }

    FacetInfo2<Module> underlyingFacet = null;
    FacetTypeId underlyingFacetType = myFacetType.getUnderlyingFacetType();
    if (underlyingFacetType != null) {
      if (myUnderlyingFacetSelector != null) {
        Map<U, FacetInfo2<Module>> underlyingFacets = myDetectedFacetSet.getConfigurations(underlyingFacetType, module);
        FacetConfiguration undelyingConfiguration =
            myUnderlyingFacetSelector.selectUnderlyingFacet(virtualFile, Collections.unmodifiableSet(underlyingFacets.keySet()));
        underlyingFacet = underlyingFacets.get(undelyingConfiguration);
      }
      if (underlyingFacet == null) {
        LOG.debug("Underlying " + underlyingFacetType + " facet not found for " + url);
        return null;
      }
    }

    Map<C, FacetInfo2<Module>> configurations = myDetectedFacetSet.getConfigurations(myFacetType.getId(), module);

    final C detectedConfiguration = myFacetDetector.detectFacet(source, Collections.unmodifiableSet(configurations.keySet()));
    if (detectedConfiguration == null) {
      return null;
    }

    if (configurations.containsKey(detectedConfiguration)) {
      return configurations.get(detectedConfiguration);
    }

    final String name = myDetectedFacetSet.generateName(module, myFacetType);
    FacetInfo2<Module> detected = myDetectedFacetSet.createInfo(module, url, underlyingFacet, detectedConfiguration, name, myFacetType, myFacetDetector.getId());
    myDetectedFacetSet.addFacetInfo(detected);
    return detected;
  }

  @Override
  public String toString() {
    return myFacetType.getStringId() + ": " + myFacetDetector.getId();
  }

  @Nullable
  public abstract FacetInfo2<Module> detectFacet(final VirtualFile virtualFile, final PsiManager psiManager);
}
