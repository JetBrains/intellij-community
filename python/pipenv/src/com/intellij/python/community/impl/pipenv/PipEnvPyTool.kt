// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.pipenv

import com.intellij.python.pytools.backend.PackageManagerPyTool
import com.intellij.python.pytools.backend.PyTool
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus

/**
 * [Pipenv](https://pipenv.pypa.io/) — a Python dependency and virtual-environment manager maintained
 * under the PyPA. It combines pip and virtualenv into a single workflow, tracking declared and locked
 * dependencies in `Pipfile` and `Pipfile.lock` and creating a per-project virtual environment.
 */
@ApiStatus.Internal
class PipEnvPyTool : PackageManagerPyTool {
  override val packageName: PyPackageName = PyPackageName.from("pipenv")

  @Suppress("CompanionObjectInExtension")
  companion object {
    fun getInstance(): PipEnvPyTool = PyTool.EP_NAME.findExtensionOrFail(PipEnvPyTool::class.java)
  }
}
