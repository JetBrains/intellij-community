/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.openapi.project.Project;

/**
 * @author nik
 */
public class FacetAutodetectingManager {
  public static FacetAutodetectingManager getInstance(Project project) {
    return project.getComponent(FacetAutodetectingManager.class);
  }
}
