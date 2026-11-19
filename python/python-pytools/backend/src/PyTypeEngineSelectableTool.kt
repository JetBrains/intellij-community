// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend

import com.intellij.openapi.project.Project

/**
 * A [ProjectLevelPyTool] the project can select as its type engine.
 */
interface PyTypeEngineSelectableTool {
  /** Returns true when the project uses this tool as its type engine. */
  fun isSelectedAsTypeEngine(project: Project): Boolean

  /**
   * Returns true when the project's type engine runs this tool's own server, not a bundled one.
   *
   * The default is [isSelectedAsTypeEngine]. A tool whose type engine runs a bundled server instead returns
   * `false`, because the engine then does not use this tool's server.
   */
  fun runsForExternalTypeEngine(project: Project): Boolean = isSelectedAsTypeEngine(project)
}
