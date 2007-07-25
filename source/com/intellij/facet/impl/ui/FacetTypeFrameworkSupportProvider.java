/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.facet.*;
import com.intellij.facet.impl.FacetUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class FacetTypeFrameworkSupportProvider<F extends Facet> extends VersionedFrameworkSupportProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ui.FacetTypeFrameworkSupportProvider");
  @NonNls private static final String FACET_SUPPORT_PREFIX = "facet:";
  private final FacetType<F, ?> myFacetType;

  protected FacetTypeFrameworkSupportProvider(FacetType<F, ?> facetType) {
    super(getProviderId(facetType), facetType.getPresentableName());
    myFacetType = facetType;
  }

  protected static String getProviderId(final FacetType facetType) {
    return FACET_SUPPORT_PREFIX + facetType.getStringId();
  }

  protected static String getProviderId(final FacetTypeId typeId) {
    FacetType type = FacetTypeRegistry.getInstance().findFacetType(typeId);
    LOG.assertTrue(type != null, typeId);
    return getProviderId(type);
  }

  @Nullable
  public String getUnderlyingFrameworkId() {
    FacetTypeId typeId = myFacetType.getUnderlyingFacetType();
    if (typeId == null) return null;

    FacetType type = FacetTypeRegistry.getInstance().findFacetType(typeId);
    return type != null ? getProviderId(type) : null;

  }

  protected void addSupport(final Module module, final ModifiableRootModel rootModel, final String version) {
    ModifiableFacetModel model = FacetManager.getInstance(module).createModifiableModel();
    Facet underlyingFacet = null;
    FacetTypeId underlyingFacetType = myFacetType.getUnderlyingFacetType();
    if (underlyingFacetType != null) {
      underlyingFacet = model.getFacetByType(underlyingFacetType);
      LOG.assertTrue(underlyingFacet != null, underlyingFacetType);
    }
    F facet = FacetUtil.createFacet(myFacetType, module, underlyingFacet);
    setupConfiguration(facet, rootModel, version);
    model.addFacet(facet);
    model.commit();
  }

  protected abstract void setupConfiguration(final F facet, final ModifiableRootModel rootModel, final String version);
}
