/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl;

import com.intellij.facet.FacetModificationTrackingService;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.HashMap;

/**
 * @author nik
 */
public class FacetModificationTrackingServiceImpl extends FacetModificationTrackingService {
  private Map<Facet, FacetModificationTracker> myModificationsTrackers = new HashMap<Facet, FacetModificationTracker>();

  public FacetModificationTrackingServiceImpl(final Module module) {
    module.getMessageBus().connect().subscribe(FacetManager.FACETS_TOPIC, new FacetModificationTrackingListener());
  }

  @NotNull
  public ModificationTracker getFacetModificationTracker(@NotNull final Facet facet) {
    FacetModificationTracker tracker = myModificationsTrackers.get(facet);
    if (tracker == null) {
      tracker = new FacetModificationTracker();
      myModificationsTrackers.put(facet, tracker);
    }
    return tracker;
  }

  private static class FacetModificationTracker implements ModificationTracker {
    private long myModificationCount;

    public long getModificationCount() {
      return myModificationCount;
    }
  }

  private class FacetModificationTrackingListener extends FacetManagerAdapter {
    public void facetConfigurationChanged(@NotNull final Facet facet) {
      final FacetModificationTracker tracker = myModificationsTrackers.get(facet);
      if (tracker != null) {
        tracker.myModificationCount++;
      }
    }

    public void facetRemoved(@NotNull final Facet facet) {
      myModificationsTrackers.remove(facet);
    }
  }
}
