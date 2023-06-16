// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.uast.testFramework.env

import com.intellij.mock.MockProject
import java.io.File

abstract class AbstractCoreEnvironment {
  abstract val project: MockProject

  open fun dispose() {
    // Do nothing
  }

  abstract fun addJavaSourceRoot(root: File)
  abstract fun addJar(root: File)
}