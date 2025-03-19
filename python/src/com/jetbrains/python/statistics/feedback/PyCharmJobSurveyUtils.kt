// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics.feedback

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.Duration


class PythonJobSurveyState : BaseState() {
  var firstLaunch by string()
}

class PythonFirstLaunchChecker : ProjectActivity {
  override suspend fun execute(project: Project) {
    val state = PythonJobSurveyService.getInstance().state ?: return
    if (state.firstLaunch == null) {
      val dateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
      state.firstLaunch = LocalDateTime.Formats.ISO.format(dateTime)
    }
  }
}


fun shouldShowSurvey(): Boolean {
  val firstLaunch = PythonJobSurveyService.getInstance().state?.firstLaunch ?: return false
  val dateTime = LocalDateTime.Formats.ISO.parse(firstLaunch)
  // Start checking on the third day of usage
  return Duration.between(dateTime.toJavaLocalDateTime(), java.time.LocalDateTime.now()).toDays() >= 3
}