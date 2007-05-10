/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import org.jetbrains.annotations.Nullable;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetInfo;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.ModuleType;

import java.util.Collection;

/**
 * @author nik
 */
public interface FacetEditorFacade {

  boolean nodeHasFacetOfType(final @Nullable FacetInfo facet, FacetTypeId typeId);

  @Nullable
  FacetInfo getSelectedFacetInfo();

  @Nullable
  ModuleType getSelectedModuleType();

  void deleteSelectedFacet();

  void createFacet(final FacetInfo parent, final FacetType type, final String name);

  Collection<FacetInfo> getFacetsByType(FacetType<?,?> type);

  @Nullable
  FacetInfo getParent(final FacetInfo facet);

  boolean isProjectVersionSupportsFacetAddition(final FacetType type);

}
