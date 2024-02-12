package com.intellij.tools.ide.performanceTesting.commands.dto

import com.intellij.tools.ide.performanceTesting.commands.SdkObject
import java.io.Serializable

data class NewMavenProjectDto(
  val projectName: String,
  val asModule: Boolean,
  val parentModuleName: String? = null,
  val mavenArchetypeInfo: MavenArchetypeInfo? = null,
  val sdkObject: SdkObject? = null
) : Serializable
