// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.shared

import org.jetbrains.annotations.Nls
import javax.swing.Icon

data class UICustomization(
  /**
   * i.e: "UV" for pythons found by UV
   */
  val title: @Nls String,
  val icon: Icon? = null,
) : Comparable<UICustomization> {
  override fun compareTo(other: UICustomization): Int = title.compareTo(other.title)
}
