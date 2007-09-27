/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author nik
 */
public interface FacetContextChangeListener extends EventListener {

  void moduleRootsChanged(ModifiableRootModel rootModel);

  void facetModelChanged(@NotNull Module module);

}
