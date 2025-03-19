// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin.dto

data class MavenGoalConfigurationDto(
  val moduleName: String,
  val goals: List<String>,
  val settingsFilePath: String = "",
  val runAnything: Boolean = false,
)
