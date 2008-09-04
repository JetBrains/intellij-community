/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.FacetType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class FacetAutodetectingManager {
  public static FacetAutodetectingManager getInstance(Project project) {
    return project.getComponent(FacetAutodetectingManager.class);
  }

  // todo for nik
  public abstract void disableAutodetectionInDirs(@NotNull Module module, @NotNull String... dirUrls);

  public abstract void disableAutodetectionInFiles(@NotNull FacetType type, @NotNull Module module, @NotNull String... fileUrls);

  public abstract void disableAutodetectionInModule(FacetType type, Module module);

  public abstract void disableAutodetectionInProject();

  public abstract void disableAutodetectionInProject(FacetType type);

  public abstract boolean hasDetectors(@NotNull FacetType<?, ?> facetType);
}
