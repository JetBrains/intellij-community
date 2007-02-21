/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.pointers;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.facet.FacetManagerListener;
import com.intellij.facet.pointers.FacetPointer;
import com.intellij.facet.pointers.FacetPointerListener;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class FacetPointersManagerImpl extends FacetPointersManager implements ApplicationComponent {
  private Map<String, FacetPointerImpl> myPointers = new HashMap<String, FacetPointerImpl>();
  private ApplicationWideModuleListener myModuleListener;
  private Map<Class<? extends Facet>, EventDispatcher<FacetPointerListener>> myDispatchers =
    new HashMap<Class<? extends Facet>, EventDispatcher<FacetPointerListener>>();

  public <F extends Facet> FacetPointer<F> create(final F facet) {
    String id = constructId(facet);
    //noinspection unchecked
    FacetPointerImpl<F> pointer = myPointers.get(id);
    if (pointer == null) {
      pointer = new FacetPointerImpl<F>(this, facet);
      myPointers.put(id, pointer);
    }
    return pointer;
  }

  public <F extends Facet> FacetPointer<F> create(final String id) {
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
    myModuleListener = new ApplicationWideModuleListener() {
      public void moduleAdded(final Module module) {
        FacetManager.getInstance(module).addListener(facetListener);
      }

      public void moduleRemoved(final Module module) {
        FacetManager.getInstance(module).removeListener(facetListener);
      }

      public void moduleRenamed(final Module module) {
        refreshPointers(module);
      }
    };
  }

  private void refreshPointers(final @NotNull Module module) {
    //todo[nik] refresh only pointers related to renamed module/facet?
    List<Pair<FacetPointerImpl, String>> changed = new ArrayList<Pair<FacetPointerImpl, String>>();

    for (FacetPointerImpl pointer : myPointers.values()) {
      final String oldId = pointer.getId();
      pointer.refresh(module.getProject());
      if (!oldId.equals(pointer.getId())) {
        changed.add(Pair.create(pointer, oldId));
      }
    }

    for (Pair<FacetPointerImpl, String> pair : changed) {
      FacetPointerImpl pointer = pair.getFirst();
      final Facet facet = pointer.getFacet(module.getProject());
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

  public void onProjectOpened(Project project) {
    myModuleListener.onProjectOpened(project);
  }

  public void onProjectClosed(Project project) {
    myModuleListener.onProjectClosed(project);
  }

  public boolean isRegistered(FacetPointer<?> pointer) {
    return myPointers.containsKey(pointer.getId());
  }

  public void disposeComponent() {
    myModuleListener.dispose();
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

}
