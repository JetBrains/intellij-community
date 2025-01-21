// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics.feedback

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class PythonShowJobSurveyAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    PythonJobSurvey().showNotification(e.project!!, true)
  }
}