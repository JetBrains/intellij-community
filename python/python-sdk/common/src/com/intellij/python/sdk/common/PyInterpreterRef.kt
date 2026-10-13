// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.common

import com.intellij.python.pytools.common.FusId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * The identity of one interpreter of a `PyProject`: where it runs ([mode]), and which environment it is there ([env]).
 *
 * It names a tool by its id, not by the tool itself, so it travels to the frontend, and it stays valid when the plugin
 * of the tool is not loaded.
 *
 * [toString] and [parse] give its string form, which is how it is stored:
 * - `native:<manager>:<env ref>` for an interpreter on the machine of the project, for example
 *   `native:uv:/home/me/app/.venv/bin/python`.
 * - `target:<target id>:<manager>:<env ref>` for an interpreter on a target, for example `target:3f2a…:conda:ml`.
 *
 * The env ref is the last part, so it can hold a colon, as a Windows path does.
 */
@ApiStatus.Internal
@Serializable
data class PyInterpreterRef(val mode: Mode, val env: PyEnvId) {
  constructor(mode: Mode, manager: FusId, envRef: PyEnvRef) : this(mode, PyEnvId(manager, envRef))

  /** The tool that manages the environment, see [PyEnvId.manager]. */
  val manager: FusId get() = env.manager

  /** The name of the environment for [manager], see [PyEnvId.envRef]. */
  val envRef: PyEnvRef get() = env.envRef

  /** Where the interpreter runs. */
  @Serializable
  sealed interface Mode {
    /** On the machine of the project. */
    @Serializable
    data object Native : Mode

    /** On the target with the configuration id [targetId]. */
    @Serializable
    data class Target(val targetId: @NonNls String) : Mode
  }

  override fun toString(): String = when (mode) {
    Mode.Native -> "$NATIVE:${manager.value}:$envRef"
    is Mode.Target -> "$TARGET:${mode.targetId}:${manager.value}:$envRef"
  }

  companion object {
    private const val NATIVE = "native"
    private const val TARGET = "target"

    /** A ref on the machine of the project. */
    fun native(manager: FusId, envRef: PyEnvRef): PyInterpreterRef = PyInterpreterRef(Mode.Native, manager, envRef)

    /** The ref that [value] states, or `null` when [value] is not in the form [toString] gives. */
    fun parse(value: @NonNls String): PyInterpreterRef? {
      val (mode, rest) = when (value.substringBefore(':')) {
        NATIVE -> Mode.Native to value.substringAfter(':')
        TARGET -> {
          val afterMode = value.substringAfter(':')
          if (!afterMode.contains(':')) return null
          Mode.Target(afterMode.substringBefore(':')) to afterMode.substringAfter(':')
        }
        else -> return null
      }
      if (!rest.contains(':')) return null
      val manager = rest.substringBefore(':').takeIf { it.isNotEmpty() } ?: return null
      val envRef = rest.substringAfter(':').takeIf { it.isNotEmpty() } ?: return null
      return PyInterpreterRef(mode, FusId(manager), PyEnvRef(envRef))
    }
  }
}

/**
 * An environment, without where it runs: the tool that manages it ([manager], by the `fusId` of its `PyTool`), and the
 * [envRef] by which that manager finds it again. Only the manager reads [envRef]: it is a path for uv, a name for hatch.
 * A [PyInterpreterRef] adds the mode.
 */
@ApiStatus.Internal
@Serializable
data class PyEnvId(val manager: FusId, val envRef: PyEnvRef)

/**
 * The part of a [PyInterpreterRef] by which its manager finds the environment again. Only the manager gives it a
 * meaning: a path to the Python binary for pip and uv, the environment name for hatch, `in-project` or a Python version
 * for poetry, the identity of the environment for conda.
 */
@ApiStatus.Internal
@Serializable
@JvmInline
value class PyEnvRef(val value: @NonNls String) {
  override fun toString(): String = value
}
