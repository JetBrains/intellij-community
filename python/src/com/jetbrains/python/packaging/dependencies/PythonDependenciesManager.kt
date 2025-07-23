// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.dependencies

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.packaging.PyRequirement
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
interface PythonDependenciesManager : Disposable.Default {
  fun isAddDependencyPossible(): Boolean = true
  fun addDependency(packageName: String): Boolean
  fun getDependencies(): List<PyRequirement>?
  fun getDependenciesFile(): VirtualFile?
}