// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend.evolution

import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.resolveExecutable
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Base class for an Evo widget node backed by a [PyTool]: the node's label, icon and availability all come from the
 * tool, so a provider only has to declare its identity and its environment layout.
 *
 * Availability is "the tool's executable resolves on this machine" for every such tool — and it is what decides whether
 * the node appears at all — so it belongs here rather than being restated per provider. Resolution goes through
 * [resolveExecutable], i.e. the tool's custom-path store plus `PyExecutableCache`, which is what lets a tool installed
 * outside `PATH` (conda under `~/miniconda3/bin`) be found, and a just-installed one be seen immediately.
 *
 * It sits beside [PyEvoEnvironmentProvider] because `python-sdk` now depends on `python-pytools` and can therefore name
 * [PyTool]. While the edge ran the other way this had to live in pytools instead.
 *
 * A node with no tool behind it — the "advanced" actions node, or the plain-virtualenv node, which is always available —
 * implements [PyEvoEnvironmentProvider] directly instead.
 */
@ApiStatus.Internal
abstract class PyToolEvoEnvironmentProvider : PyEvoEnvironmentProvider {
  /**
   * The tool behind this node. A property rather than a constructor parameter so the lookup stays lazy: providers are
   * instantiated when the extension list is first read, which is not a good time to reach for a service.
   */
  protected abstract val tool: PyTool

  override val label: @Nls String get() = tool.presentableName

  override val icon: Icon get() = tool.icon

  override suspend fun isAvailable(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>): Boolean =
    executableOrNull(fileSystem) != null

  /**
   * This tool's executable on [fileSystem], or `null` when it is not installed there.
   *
   * Kept as a [PathHolder.Eel] rather than unwrapped to a [java.nio.file.Path], since that is what the tools' own
   * setup functions take.
   *
   * [loadSections] is only called for a node [isAvailable] admitted, but the two are separate calls with the detection
   * cache's TTL between them, so a provider that needs the executable still has to handle its absence.
   */
  protected suspend fun executableOrNull(fileSystem: FileSystem<PathHolder.Eel>): PathHolder.Eel? =
    tool.resolveExecutable(fileSystem)
}
