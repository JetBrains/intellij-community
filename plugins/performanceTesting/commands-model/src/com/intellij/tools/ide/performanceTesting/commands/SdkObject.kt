// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.performanceTesting.commands

import java.io.Serializable
import java.nio.file.Path

data class SdkObject(
  val sdkName: String,
  val sdkType: String,
  val sdkPath: Path,
) : Serializable {
  override fun toString(): String {
    return "$sdkName: $sdkType"
  }
}