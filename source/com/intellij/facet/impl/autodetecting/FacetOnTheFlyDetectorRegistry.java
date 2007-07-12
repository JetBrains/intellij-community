/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface FacetOnTheFlyDetectorRegistry<C extends FacetConfiguration> {

  void register(@NotNull FileType fileType, @NotNull VirtualFileFilter virtualFileFilter,
                @NotNull FacetDetector<VirtualFile, C> detector);

  void register(@NotNull FileType fileType, @NotNull VirtualFileFilter virtualFileFilter,
                @NotNull Condition<PsiFile> psiFileFilter, @NotNull FacetDetector<PsiFile, C> detector);
}
