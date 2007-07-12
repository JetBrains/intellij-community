/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

/**
 * @author nik
 */
public class FacetByPsiFileDetectorWrapper<C extends FacetConfiguration, F extends Facet<C>> extends FacetDetectorWrapper<PsiFile, C, F> {
  private Condition<PsiFile> myPsiFileFilter;

  public FacetByPsiFileDetectorWrapper(final FileType fileType, FacetType<F, C> facetType,
                                       final AutodetectionFilter autodetectionFilter, final VirtualFileFilter virtualFileFilter,
                                       final FacetDetector<PsiFile, C> facetDetector,
                                       Condition<PsiFile> psiFileFilter) {
    super(fileType, facetType, autodetectionFilter, virtualFileFilter, facetDetector);
    myPsiFileFilter = psiFileFilter;
  }

  public Facet detectFacet(final VirtualFile virtualFile, final PsiManager psiManager) {
    PsiFile psiFile = psiManager.findFile(virtualFile);
    if (psiFile == null || !myPsiFileFilter.value(psiFile)) {
      return null;
    }

    Module module = ModuleUtil.findModuleForFile(virtualFile, psiManager.getProject());
    if (module == null) return null;

    return detectFacet(module, virtualFile, psiFile);
  }
}
