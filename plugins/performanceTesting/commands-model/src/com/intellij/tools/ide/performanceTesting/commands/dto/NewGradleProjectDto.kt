package com.intellij.tools.ide.performanceTesting.commands.dto

import com.intellij.tools.ide.performanceTesting.commands.SdkObject
import java.io.Serializable

data class NewGradleProjectDto(
  val projectName: String,
  val asModule: Boolean,
  val gradleDSL: GradleDSL = GradleDSL.GROOVY,
  val parentModuleName: String? = null,
  val sdkObject: SdkObject? = null
) : Serializable
