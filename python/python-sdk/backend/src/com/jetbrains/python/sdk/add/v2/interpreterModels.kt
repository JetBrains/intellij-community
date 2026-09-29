// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.python.community.services.shared.PythonInfoHolder
import com.intellij.python.community.services.shared.PythonInfoWithUiComparator
import com.intellij.python.community.services.shared.UiHolder
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.PythonInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MaybeSystemPython {
  /**
   * System python can be used as a base python for other envs
   */
  val isBase: Boolean
}

/**
 * [PythonSelectableInterpreter] that always has a [homePath].
 * All types except [InstallableSelectableInterpreter] implement this interface.
 */
sealed interface InterpreterWithPath<P : PathHolder> {
  val homePath: P
}

sealed class PythonSelectableInterpreter<P : PathHolder> : Comparable<PythonSelectableInterpreter<*>>, UiHolder, PythonInfoHolder {
  companion object {
    private val comparator = PythonInfoWithUiComparator<PythonSelectableInterpreter<*>>()
  }

  /**
   * Is `null` only for [InstallableSelectableInterpreter].
   * To get a non-null value, use pattern matching or [InterpreterWithPath].
   */
  abstract val homePath: P?
  abstract override val pythonInfo: PythonInfo
  override val ui: PyToolUIInfo? = null
  override fun toString(): String = "PythonSelectableInterpreter(homePath='${homePath?.toStringForUI()}')"

  override fun compareTo(other: PythonSelectableInterpreter<*>): Int = comparator.compare(this, other)
}

class ExistingSelectableInterpreter<P : PathHolder>(
  val pythonInterpreterWrapper: PythonInterpreterWrapper<P>,
  override val pythonInfo: PythonInfo,
  val isSystemWide: Boolean,
) : PythonSelectableInterpreter<P>(), InterpreterWithPath<P> {
  override val homePath: P
    get() = pythonInterpreterWrapper.homePath

  override fun toString(): String {
    return "ExistingSelectableInterpreter(sdk=${pythonInterpreterWrapper.pythonInterpreter}, pythonInfo=$pythonInfo, isSystemWide=$isSystemWide, homePath='${homePath.toStringForUI()}')"
  }
}

class ManuallyAddedSelectableInterpreter<P : PathHolder>(
  override val homePath: P,
  override val pythonInfo: PythonInfo,
  override val isBase: Boolean,
) : PythonSelectableInterpreter<P>(), MaybeSystemPython, InterpreterWithPath<P> {
  override fun toString(): String {
    return "ManuallyAddedSelectableInterpreter(homePath='${homePath.toStringForUI()}', pythonInfo=$pythonInfo)"
  }
}

class InstallableSelectableInterpreter<P : PathHolder>(
  override val pythonInfo: PythonInfo,
  val installableSdk: InstallablePythonSdk,
) : PythonSelectableInterpreter<P>() {
  override val homePath: Nothing?
    get() = null
}

/**
 * [isBase] is a system interpreter (aka system python)
 */
class DetectedSelectableInterpreter<P : PathHolder>(
  override val homePath: P,
  override val pythonInfo: PythonInfo,
  override val isBase: Boolean,
  override val ui: PyToolUIInfo? = null,
) : PythonSelectableInterpreter<P>(), MaybeSystemPython, InterpreterWithPath<P> {
  override fun toString(): String {
    return "DetectedSelectableInterpreter(homePath='${homePath.toStringForUI()}', pythonInfo=$pythonInfo, isBase=$isBase, uiCustomization=$ui)"
  }
}
