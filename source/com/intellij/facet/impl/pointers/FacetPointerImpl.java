/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.pointers;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.pointers.FacetPointer;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class FacetPointerImpl<F extends Facet> implements FacetPointer<F> {
  private String myModuleName;
  private String myFacetTypeId;
  private String myFacetName;
  private F myFacet;

  public FacetPointerImpl(final String moduleName, final String facetTypeId, final String facetName) {
    myModuleName = moduleName;
    myFacetTypeId = facetTypeId;
    myFacetName = facetName;
  }

  public FacetPointerImpl(String id) {
    final int i = id.indexOf('/');
    myModuleName = id.substring(0, i);

    final int j = id.lastIndexOf('/');
    myFacetTypeId = id.substring(i + 1, j);
    myFacetName = id.substring(j+1);
  }

  public FacetPointerImpl(final @NotNull F facet) {
    myFacet = facet;
    updateInfo(myFacet);
    registerDisposable();
  }

  public void refresh(@NotNull Project project) {
    findAndSetFacet(project);

    if (myFacet != null) {
      updateInfo(myFacet);
    }
  }

  private void findAndSetFacet(final Project project) {
    if (myFacet == null) {
      myFacet = findFacet(project);
      if (myFacet != null) {
        registerDisposable();
      }
    }
  }

  private void registerDisposable() {
    Disposer.register(myFacet, new Disposable() {
      public void dispose() {
        FacetPointersManager.getInstance().dispose(FacetPointerImpl.this);
      }
    });
  }

  private void updateInfo(final @NotNull F facet) {
    myModuleName = facet.getModule().getName();
    myFacetTypeId = facet.getType().getStringId();
    myFacetName = facet.getName();
  }

  public F getFacet(@NotNull final Project project) {
    findAndSetFacet(project);
    return myFacet;
  }

  @Nullable
  private F findFacet(final Project project) {
    final Module module = ModuleManager.getInstance(project).findModuleByName(myModuleName);
    if (module == null) return null;

    final FacetType<F, ?> type = getFacetType();
    if (type == null) return null;

    return FacetManager.getInstance(module).findFacet(type.getId(), myFacetName);
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public String getFacetName() {
    return myFacetName;
  }

  @NotNull
  public String getId() {
    return FacetPointersManager.constructId(myModuleName, myFacetTypeId, myFacetName);
  }

  @Nullable
  public FacetType<F, ?> getFacetType() {
    FacetType type = FacetTypeRegistry.getInstance().findFacetType(myFacetTypeId);
    if (type == null) {
      type = findJavaeeFacetType(myFacetTypeId);
    }
    //noinspection unchecked
    return type;
  }


  //todo[nik] remove when FacetType for javaee facets will be registered in FacetTypeRegistry
  private static List<FacetType<?, ?>> ourAdditionalFacetTypes = new ArrayList<FacetType<?,?>>();

  public static void registerAdditionalFacetType(FacetType<?, ?> facetType) {
    ourAdditionalFacetTypes.add(facetType);
  }

  @Nullable
  private static FacetType<?, ?> findJavaeeFacetType(@NotNull @NonNls String typeId) {
    for (FacetType<?, ?> facetType : ourAdditionalFacetTypes) {
      if (facetType.getStringId().equals(typeId)) {
        return facetType;
      }
    }
    return null;
  }

}
