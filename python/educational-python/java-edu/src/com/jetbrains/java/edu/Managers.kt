// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.java.edu

import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.module.impl.ModuleTypeManagerImpl
import com.jetbrains.python.PlatformPythonModuleType
import org.jetbrains.annotations.NonNls

class CompositeModuleTypeManagerImpl : ModuleTypeManagerImpl() {

  init {
    registerModuleType(PlatformPythonModuleType())
  }

  override fun getDefaultModuleType(): ModuleType<*> {
    return StdModuleTypes.JAVA
  }

  override fun findByID(moduleTypeID: String?): ModuleType<*> {
    if (moduleTypeID != null) {
      if (JAVA_MODULE_ID_OLD == moduleTypeID) {
        return StdModuleTypes.JAVA // for compatibility with the previous ID that Java modules had
      }
    }
    return super.findByID(moduleTypeID)
  }

  companion object {
    @NonNls private val JAVA_MODULE_ID_OLD = "JAVA"
  }
}