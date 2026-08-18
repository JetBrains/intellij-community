// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.OverrideOnly
fun interface PySdkToFacetSetter {
  /**
   * Set [sdk] to [module] for non-python module (any non-opython module might have python SDK as a facet)
   */
  @RequiresWriteLock
  fun setPythonSdkToFacet(module: Module, sdk: Sdk?)

}