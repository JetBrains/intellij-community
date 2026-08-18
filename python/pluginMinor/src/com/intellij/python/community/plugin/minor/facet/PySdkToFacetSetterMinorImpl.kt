// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.minor.facet

import com.intellij.facet.FacetManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.jetbrains.python.module.PySdkToFacetSetter

internal class PySdkToFacetSetterMinorImpl : PySdkToFacetSetter {
  @RequiresWriteLock
  override fun setPythonSdkToFacet(module: Module, sdk: Sdk?) {
    setSdkToFacet(sdk, module)
  }
}
