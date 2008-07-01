/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.impl.autodetecting.model.FacetInfo2;
import com.intellij.facet.impl.autodetecting.model.ProjectFacetInfoSet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class FacetByVirtualFileDetectorWrapper<C extends FacetConfiguration, F extends Facet<C>, U extends FacetConfiguration> extends FacetDetectorWrapper<VirtualFile, C, F, U> {
  public FacetByVirtualFileDetectorWrapper(ProjectFacetInfoSet projectFacetSet, FacetType<F, C> facetType,
                                           final AutodetectionFilter autodetectionFilter, final VirtualFileFilter virtualFileFilter,
                                           final FacetDetector<VirtualFile, C> facetDetector) {
    super(projectFacetSet, facetType, autodetectionFilter, virtualFileFilter, facetDetector, null);
  }

  @Nullable
  public FacetInfo2<Module> detectFacet(final VirtualFile virtualFile, final PsiManager psiManager) {
    Module module = ModuleUtil.findModuleForFile(virtualFile, psiManager.getProject());
    if (module == null) {
      return null;
    }
    return detectFacet(module, virtualFile, virtualFile);
  }
}
