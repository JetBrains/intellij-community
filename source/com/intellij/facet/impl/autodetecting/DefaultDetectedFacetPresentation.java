/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.Facet;
import com.intellij.facet.autodetecting.DetectedFacetPresentation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class DefaultDetectedFacetPresentation extends DetectedFacetPresentation {
  public static final DefaultDetectedFacetPresentation INSTANCE = new DefaultDetectedFacetPresentation();

  @NotNull
  public String getAutodetectionPopupText(@NotNull final Facet facet, @NotNull final VirtualFile[] files) {
    Module module = facet.getModule();
    String fileUrl = ImplicitFacetInfo.getRelativeFileUrl(files[0], module.getProject());
    return ProjectBundle.message("facet.autodetected.popup.default.text", fileUrl, module.getName(), facet.getType().getPresentableName(), facet.getName());
  }
}
