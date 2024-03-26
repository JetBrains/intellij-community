package com.intellij.tools.ide.performanceTesting.commands.dto

import com.intellij.tools.ide.performanceTesting.commands.SdkObject
import java.io.Serializable

data class NewSpringProjectDto(
  val projectName: String,
  val buildType: BuildType,
  val frameworkVersion: String,
  val sdkObject: SdkObject? = null
) : Serializable
