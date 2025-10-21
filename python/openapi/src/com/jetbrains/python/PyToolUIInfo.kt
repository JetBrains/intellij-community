// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * Various tools might have name and icon
 */
@ApiStatus.Internal
data class PyToolUIInfo(
  /**
   * i.e: "UV" for pythons found by UV
   */
  val toolName: @NlsSafe String, // Tools are usually untranslatable
  val icon: Icon? = null,
) : Comparable<PyToolUIInfo> {
  override fun compareTo(other: PyToolUIInfo): Int = toolName.compareTo(other.toolName)
}