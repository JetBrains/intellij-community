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