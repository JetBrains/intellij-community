// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.pipenv

import com.intellij.python.community.impl.pipenv.PyPipenvBundle.message
import com.intellij.python.community.impl.pipenv.icons.PythonCommunityImplPipenvIcons
import com.intellij.python.pytools.PyTool
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * [Pipenv](https://pipenv.pypa.io/) — a Python dependency and virtual-environment manager maintained
 * under the PyPA. It combines pip and virtualenv into a single workflow, tracking declared and locked
 * dependencies in `Pipfile` and `Pipfile.lock` and creating a per-project virtual environment.
 */
@ApiStatus.Internal
class PipEnvPyTool : PyTool {
  override val presentableName: String = "Pipenv"
  override val packageName: PyPackageName = PyPackageName.from("pipenv")
  override val description: String get() = message("python.pipenv.tool.description")
  // TODO: Provide a special icon for pipenv
  override val icon: Icon get() = PythonCommunityImplPipenvIcons.PythonClosed

  @Suppress("CompanionObjectInExtension")
  companion object {
    fun getInstance(): PipEnvPyTool = PyTool.EP_NAME.findExtensionOrFail(PipEnvPyTool::class.java)
  }
}
