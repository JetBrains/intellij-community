/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.FacetConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class UnderlyingFacetSelector<T, U extends FacetConfiguration> {

  @Nullable
  public abstract U selectUnderlyingFacet(T source, Collection<U> underlyingFacets);

}
