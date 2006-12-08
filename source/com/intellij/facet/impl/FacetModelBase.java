/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetModel;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.util.MultiValuesMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public abstract class FacetModelBase implements FacetModel {
  private Map<FacetTypeId, Collection<Facet>> myType2Facets;
  private Facet[] mySortedFacets;

  @NotNull
  public Facet[] getSortedFacets() {
    if (mySortedFacets == null) {
      LinkedHashSet<Facet> facets = new LinkedHashSet<Facet>();
      for (Facet facet : getAllFacets()) {
        addUnderlyingFacets(facets, facet);
      }
      mySortedFacets = facets.toArray(new Facet[facets.size()]);
    }
    return mySortedFacets;
  }

  private static void addUnderlyingFacets(final LinkedHashSet<Facet> facets, final Facet facet) {
    final Facet underlyingFacet = facet.getUnderlyingFacet();
    if (underlyingFacet != null && !facets.contains(facet)) {
      addUnderlyingFacets(facets, underlyingFacet);
    }
    facets.add(facet);
  }

  @Nullable
  public <F extends Facet> F getFacetByType(FacetTypeId<F> typeId) {
    final Collection<F> fasets = getFacetsByType(typeId);
    return fasets.isEmpty() ? null : fasets.iterator().next();
  }

  @NotNull
    public <F extends Facet> Collection<F> getFacetsByType(FacetTypeId<F> typeId) {
      if (myType2Facets == null) {
        MultiValuesMap<FacetTypeId, Facet> type2Facets = new MultiValuesMap<FacetTypeId, Facet>();
        myType2Facets = new HashMap<FacetTypeId, Collection<Facet>>();
        for (Facet facet : getAllFacets()) {
          type2Facets.put(facet.getTypeId(), facet);
        }
        for (FacetTypeId id : type2Facets.keySet()) {
          final Collection<Facet> facets = type2Facets.get(id);
          myType2Facets.put(id, Collections.unmodifiableCollection(facets));
        }
      }

      final Collection<F> facets = (Collection<F>)myType2Facets.get(typeId);
      return facets != null ? facets : Collections.<F>emptyList();
    }

  protected void facetsChanged() {
    myType2Facets = null;
    mySortedFacets = null;
  }
}
