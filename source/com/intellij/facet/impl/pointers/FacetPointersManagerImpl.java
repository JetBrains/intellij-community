/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.pointers;

import com.intellij.ProjectTopics;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.facet.FacetManagerListener;
import com.intellij.facet.impl.FacetUtil;
import com.intellij.facet.pointers.FacetPointer;
import com.intellij.facet.pointers.FacetPointerListener;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.EventDispatcher;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class FacetPointersManagerImpl extends FacetPointersManager implements ProjectComponent {
  private Project myProject;
  private Map<String, FacetPointerImpl> myPointers = new HashMap<String, FacetPointerImpl>();
  private Map<Class<? extends Facet>, EventDispatcher<FacetPointerListener>> myDispatchers =
    new HashMap<Class<? extends Facet>, EventDispatcher<FacetPointerListener>>();

  public FacetPointersManagerImpl(final Project project) {
    myProject = project;
  }

  public <F extends Facet> FacetPointer<F> create(final F facet) {
    String id = constructId(facet);
    //noinspection unchecked
    FacetPointerImpl<F> pointer = myPointers.get(id);
    if (pointer == null) {
      if (!FacetUtil.isRegistered(facet)) {
        return create(id);
      }
      pointer = new FacetPointerImpl<F>(this, facet);
      myPointers.put(id, pointer);
    }
    return pointer;
  }

  public <F extends Facet> FacetPointer<F> create(final String id) {
    //noinspection unchecked
    FacetPointerImpl<F> pointer = myPointers.get(id);
    if (pointer == null) {
      pointer = new FacetPointerImpl<F>(this, id);
      myPointers.put(id, pointer);
    }
    return pointer;
  }

  <F extends Facet> void dispose(final FacetPointer<F> pointer) {
    myPointers.remove(pointer.getId());
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "FacetPointersManager";
  }

  public void initComponent() {
    final FacetManagerListener facetListener = new FacetManagerAdapter() {
      public void facetRenamed(@NotNull final Facet facet, @NotNull final String oldName) {
        refreshPointers(facet.getModule());
      }
    };
    final MyModuleListener moduleListener = new MyModuleListener(facetListener);
    myProject.getMessageBus().connect().subscribe(ProjectTopics.MODULES, moduleListener);
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      moduleListener.moduleAdded(myProject, module);
    }
  }

  private void refreshPointers(final @NotNull Module module) {
    //todo[nik] refresh only pointers related to renamed module/facet?
    List<Pair<FacetPointerImpl, String>> changed = new ArrayList<Pair<FacetPointerImpl, String>>();

    for (FacetPointerImpl pointer : myPointers.values()) {
      final String oldId = pointer.getId();
      pointer.refresh();
      if (!oldId.equals(pointer.getId())) {
        changed.add(Pair.create(pointer, oldId));
      }
    }

    for (Pair<FacetPointerImpl, String> pair : changed) {
      FacetPointerImpl pointer = pair.getFirst();
      final Facet facet = pointer.getFacet();
      Class facetClass = facet != null ? facet.getClass() : Facet.class;
      while (facetClass != Object.class) {
        final EventDispatcher<FacetPointerListener> dispatcher = myDispatchers.get(facetClass);
        if (dispatcher != null) {
          //noinspection unchecked
          dispatcher.getMulticaster().pointerIdChanged(pointer, pair.getSecond());
        }
        facetClass = facetClass.getSuperclass();
      }
    }
  }

  public boolean isRegistered(FacetPointer<?> pointer) {
    return myPointers.containsKey(pointer.getId());
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void disposeComponent() {
  }

  public void addListener(final FacetPointerListener<Facet> listener) {
    addListener(Facet.class, listener);
  }

  public void removeListener(final FacetPointerListener<Facet> listener) {
    removeListener(Facet.class, listener);
  }

  public void addListener(final FacetPointerListener<Facet> listener, final Disposable parentDisposable) {
    addListener(Facet.class, listener, parentDisposable);
  }

  public <F extends Facet> void addListener(final Class<F> facetClass, final FacetPointerListener<F> listener, final Disposable parentDisposable) {
    addListener(facetClass, listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeListener(facetClass, listener);
      }
    });
  }

  public <F extends Facet> void addListener(final Class<F> facetClass, final FacetPointerListener<F> listener) {
    EventDispatcher<FacetPointerListener> dispatcher = myDispatchers.get(facetClass);
    if (dispatcher == null) {
      dispatcher = EventDispatcher.create(FacetPointerListener.class);
      myDispatchers.put(facetClass, dispatcher);
    }
    dispatcher.addListener(listener);
  }

  public <F extends Facet> void removeListener(final Class<F> facetClass, final FacetPointerListener<F> listener) {
    EventDispatcher<FacetPointerListener> dispatcher = myDispatchers.get(facetClass);
    if (dispatcher != null) {
      dispatcher.removeListener(listener);
    }
  }

  public Project getProject() {
    return myProject;
  }

  private class MyModuleListener extends ModuleAdapter {
    private final FacetManagerListener myFacetListener;
    private Map<Module, MessageBusConnection> myModule2Connection = new HashMap<Module, MessageBusConnection>();

    public MyModuleListener(final FacetManagerListener facetListener) {
      myFacetListener = facetListener;
    }

    public void moduleAdded(Project project, final Module module) {
      final MessageBusConnection connection = module.getMessageBus().connect();
      myModule2Connection.put(module, connection);
      connection.subscribe(FacetManager.FACETS_TOPIC, myFacetListener);
    }

    public void moduleRemoved(Project project, final Module module) {
      final MessageBusConnection connection = myModule2Connection.remove(module);
      if (connection != null) {
        connection.disconnect();
      }
    }

    public void modulesRenamed(Project project, final List<Module> modules) {
      for (Module module : modules) {
        refreshPointers(module);
      }
    }
  }
}
