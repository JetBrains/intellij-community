/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui;

import com.intellij.openapi.roots.ModifiableRootModel;

import java.util.EventListener;

/**
 * @author nik
 */
public interface ModuleRootsChangeListener extends EventListener {

  void moduleRootsChanged(ModifiableRootModel rootModel);

}
