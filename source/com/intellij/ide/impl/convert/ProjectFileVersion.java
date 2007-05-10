/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.impl.convert;

import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.project.Project;

import java.io.IOException;

/**
 * @author nik
 */
public abstract class ProjectFileVersion {
  public static ProjectFileVersion getInstance(Project project) {
    return project.getComponent(ProjectFileVersion.class);
  }


  public abstract boolean isFacetAdditionEnabled(FacetTypeId<?> facetType);

  public abstract boolean isFacetDeletionEnabled(FacetTypeId<?> facetType);

  public abstract boolean isConverted();

  public abstract boolean convert() throws IOException;
}
