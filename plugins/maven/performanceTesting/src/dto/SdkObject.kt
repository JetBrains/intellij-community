package com.intellij.maven.performanceTesting.dto

import java.nio.file.Path

data class SdkObject(
  val sdkName: String,
  val sdkType: String,
  val sdkPath: Path,
)