package com.intellij.tools.ide.performanceTesting.commands

import java.nio.file.Path

data class SdkObject(
  val sdkName: String,
  val sdkType: String,
  val sdkPath: Path,
)