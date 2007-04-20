/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl;

import com.intellij.ProjectTopics;
import com.intellij.facet.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ProjectWideFacetListenersRegistryImpl extends ProjectWideFacetListenersRegistry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ProjectWideFacetListenersRegistryImpl");
  private Map<FacetTypeId, EventDispatcher<ProjectWideFacetListener>> myDispatchers = new HashMap<FacetTypeId, EventDispatcher<ProjectWideFacetListener>>();
  private Map<FacetTypeId, Integer> myFacetCounts = new HashMap<FacetTypeId, Integer>();
  private Map<Module, MessageBusConnection> myModule2Connection = new HashMap<Module, MessageBusConnection>();
  private FacetManagerAdapter myFacetListener;

  public ProjectWideFacetListenersRegistryImpl(MessageBus messageBus) {
    myFacetListener = new MyFacetManagerAdapter();
    messageBus.connect().subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      public void moduleAdded(Project project, Module module) {
        onModuleAdded(module);
      }

      public void moduleRemoved(Project project, Module module) {
        onModuleRemoved(module);
      }
    });
  }

  private void onModuleRemoved(final Module module) {
    final MessageBusConnection connection = myModule2Connection.remove(module);
    if (connection != null) {
      connection.disconnect();
    }

    final FacetManager facetManager = FacetManager.getInstance(module);
    final Facet[] facets = facetManager.getAllFacets();
    for (Facet facet : facets) {
      onFacetRemoved(facet);
    }
  }

  private void onModuleAdded(final Module module) {
    final FacetManager facetManager = FacetManager.getInstance(module);
    final Facet[] facets = facetManager.getAllFacets();
    for (Facet facet : facets) {
      onFacetAdded(facet);
    }
    final MessageBusConnection connection = module.getMessageBus().connect();
    myModule2Connection.put(module, connection);
    connection.subscribe(FacetManager.FACETS_TOPIC, myFacetListener);
  }

  private void onFacetRemoved(final Facet facet) {
    final FacetTypeId typeId = facet.getTypeId();
    Integer count = myFacetCounts.get(typeId);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (count == null || count <= 0) count = 1;
    }
    LOG.assertTrue(count != null);
    count--;
    boolean lastFacet = count == 0;
    myFacetCounts.put(typeId, count);
    final EventDispatcher<ProjectWideFacetListener> dispatcher = myDispatchers.get(typeId);
    if (dispatcher != null) {
      //noinspection unchecked
      dispatcher.getMulticaster().facetRemoved(facet);
      if (lastFacet) {
        dispatcher.getMulticaster().allFacetsRemoved();
      }
    }
  }

  private void onFacetAdded(final Facet facet) {
    final FacetTypeId typeId = facet.getTypeId();
    Integer count = myFacetCounts.get(typeId);
    if (count == null) count = 0;
    boolean firstFacet = count == 0;
    count++;
    myFacetCounts.put(typeId, count);

    final EventDispatcher<ProjectWideFacetListener> dispatcher = myDispatchers.get(typeId);
    if (dispatcher != null) {
      if (firstFacet) {
        dispatcher.getMulticaster().firstFacetAdded();
      }
      //noinspection unchecked
      dispatcher.getMulticaster().facetAdded(facet);
    }
  }

  private void onFacetChanged(final Facet facet) {
    final EventDispatcher<ProjectWideFacetListener> dispatcher = myDispatchers.get(facet.getTypeId());
    if (dispatcher != null) {
      //noinspection unchecked
      dispatcher.getMulticaster().facetConfigurationChanged(facet);
    }
  }

  public <F extends Facet> void registerListener(@NotNull FacetTypeId<F> typeId, @NotNull ProjectWideFacetListener<? extends F> listener) {
    EventDispatcher<ProjectWideFacetListener> dispatcher = myDispatchers.get(typeId);
    if (dispatcher == null) {
      dispatcher = EventDispatcher.create(ProjectWideFacetListener.class);
      myDispatchers.put(typeId, dispatcher);
    }
    dispatcher.addListener(listener);
  }

  public <F extends Facet> void unregisterListener(@NotNull FacetTypeId<F> typeId, @NotNull ProjectWideFacetListener<? extends F> listener) {
    myDispatchers.get(typeId).removeListener(listener);
  }

  public <F extends Facet> void registerListener(@NotNull final FacetTypeId<F> typeId, @NotNull final ProjectWideFacetListener<? extends F> listener,
                                                 @NotNull final Disposable parentDisposable) {
    registerListener(typeId, listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        unregisterListener(typeId, listener);
      }
    });
  }

  private class MyFacetManagerAdapter extends FacetManagerAdapter {

    public void facetAdded(@NotNull Facet facet) {
      onFacetAdded(facet);
    }

    public void facetRemoved(@NotNull Facet facet) {
      onFacetRemoved(facet);
    }

    public void facetConfigurationChanged(@NotNull final Facet facet) {
      onFacetChanged(facet);
    }

  }
}
