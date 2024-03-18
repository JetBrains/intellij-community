package org.jetbrains.idea.maven.performancePlugin.dto

import java.nio.file.Path

data class SdkObject(
  val sdkName: String,
  val sdkType: String,
  val sdkPath: Path,
)