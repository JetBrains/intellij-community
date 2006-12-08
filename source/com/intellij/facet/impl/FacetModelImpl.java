/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl;

import com.intellij.facet.ModifiableFacetModel;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.Facet;
import com.intellij.openapi.diagnostic.Logger;

import java.util.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class FacetModelImpl extends FacetModelBase implements ModifiableFacetModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.FacetModelImpl");
  private List<Facet> myFacets = new ArrayList<Facet>();
  private FacetManagerImpl myManager;

  public FacetModelImpl(final FacetManagerImpl manager) {
    myManager = manager;
    for (Facet facet : myManager.getAllFacets()) {
      addFacet(facet);
    }
  }

  public void addFacet(Facet facet) {
    if (myFacets.contains(facet)) {
      LOG.error("Facet " + facet + " [" + facet.getTypeId() + "] is already added");
    }

    myFacets.add(facet);
    facetsChanged();
  }

  public void removeFacet(Facet facet) {
    if (myFacets == null || !myFacets.remove(facet)) {
      LOG.error("Facet " + facet + " [" + facet.getTypeId() + "] not found");
    }
    facetsChanged();
  }

  public void commit() {
    myManager.commit(this);
  }

  public boolean isModified() {
    return !new HashSet<Facet>(myFacets).equals(new HashSet<Facet>(Arrays.asList(myManager.getAllFacets())));
  }

  @NotNull
  public Facet[] getAllFacets() {
    return myFacets.toArray(new Facet[myFacets.size()]);
  }

}
