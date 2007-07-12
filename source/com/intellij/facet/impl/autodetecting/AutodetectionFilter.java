/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.openapi.module.Module;
import com.intellij.facet.FacetType;

/**
 * @author nik
 */
public interface AutodetectionFilter {

  boolean isAutodetectionEnabled(Module module, FacetType facetType, final String url);

}
