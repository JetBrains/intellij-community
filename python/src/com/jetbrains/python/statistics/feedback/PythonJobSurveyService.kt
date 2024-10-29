// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics.feedback

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
@State(name="PythonJobSurveyService", storages = [Storage("pycharm-job-survey-service.xml")])
class PythonJobSurveyService : PersistentStateComponent<PythonJobSurveyState> {
  private var state = PythonJobSurveyState()

  override fun getState(): PythonJobSurveyState? = state

  override fun loadState(state: PythonJobSurveyState) {
    this.state = state
  }

  companion object {
    @JvmStatic
    fun getInstance(): PythonJobSurveyService = service()
  }
}