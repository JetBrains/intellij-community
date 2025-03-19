package com.intellij.tools.ide.performanceTesting.commands.dto

import java.io.Serializable

data class MavenGoalConfigurationDto(
  val moduleName: String = "",
  val goals: List<String>,
  val settingsFilePath: String = "",
  val runAnything: Boolean = false
) : Serializable