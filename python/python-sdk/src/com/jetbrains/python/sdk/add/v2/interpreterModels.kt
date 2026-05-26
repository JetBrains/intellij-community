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

sealed class PythonSelectableInterpreter<P : PathHolder> : Comparable<PythonSelectableInterpreter<*>>, UiHolder, PythonInfoHolder {
  companion object {
    private val comparator = PythonInfoWithUiComparator<PythonSelectableInterpreter<*>>()
  }

  abstract val homePath: P?
  abstract override val pythonInfo: PythonInfo
  override val ui: PyToolUIInfo? = null
  override fun toString(): String = "PythonSelectableInterpreter(homePath='$homePath')"

  override fun compareTo(other: PythonSelectableInterpreter<*>): Int = comparator.compare(this, other)
}

class ExistingSelectableInterpreter<P : PathHolder>(
  val sdkWrapper: SdkWrapper<P>,
  override val pythonInfo: PythonInfo,
  val isSystemWide: Boolean,
) : PythonSelectableInterpreter<P>() {
  override val homePath: P
    get() = sdkWrapper.homePath

  override fun toString(): String {
    return "ExistingSelectableInterpreter(sdk=${sdkWrapper.sdk}, pythonInfo=$pythonInfo, isSystemWide=$isSystemWide, homePath='$homePath')"
  }
}

class ManuallyAddedSelectableInterpreter<P : PathHolder>(
  override val homePath: P,
  override val pythonInfo: PythonInfo,
  override val isBase: Boolean,
) : PythonSelectableInterpreter<P>(), MaybeSystemPython {
  override fun toString(): String {
    return "ManuallyAddedSelectableInterpreter(homePath='$homePath', pythonInfo=$pythonInfo)"
  }
}

class InstallableSelectableInterpreter<P : PathHolder>(
  override val pythonInfo: PythonInfo,
  val installableSdk: InstallablePythonSdk,
) : PythonSelectableInterpreter<P>() {
  override val homePath: P? = null
}

/**
 * [isBase] is a system interpreter (aka system python)
 */
class DetectedSelectableInterpreter<P : PathHolder>(
  override val homePath: P,
  override val pythonInfo: PythonInfo,
  override val isBase: Boolean,
  override val ui: PyToolUIInfo? = null,
) : PythonSelectableInterpreter<P>(), MaybeSystemPython {
  override fun toString(): String {
    return "DetectedSelectableInterpreter(homePath='$homePath', pythonInfo=$pythonInfo, isBase=$isBase, uiCustomization=$ui)"
  }
}
