// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.experiment

import org.jetbrains.annotations.ApiStatus

@Deprecated("Left for compatibility. Will be removed in future release.")
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
interface WebServiceStatus {
  companion object {
    private val fakeWebServiceStatus = object : WebServiceStatus {
      override fun isServerOk(): Boolean = false
      override fun dataServerUrl(): String = ""
      override fun isExperimentOnCurrentIDE(): Boolean = false
      override fun experimentVersion(): Int = 2
      override fun updateStatus() = Unit
    }
    fun getInstance() = fakeWebServiceStatus
  }

  fun isServerOk(): Boolean
  fun dataServerUrl(): String
  fun isExperimentOnCurrentIDE(): Boolean
  fun experimentVersion(): Int
  fun updateStatus()
}

