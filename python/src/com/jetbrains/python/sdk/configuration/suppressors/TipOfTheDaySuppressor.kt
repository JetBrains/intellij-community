// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration.suppressors

import com.intellij.ide.GeneralSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger

internal class TipOfTheDaySuppressor private constructor() : Disposable {

  private val savedValue: Boolean

  companion object {
    private val LOGGER = Logger.getInstance(TipOfTheDaySuppressor::class.java)

    fun suppress(): Disposable? {
      return if (!GeneralSettings.getInstance().isShowTipsOnStartup) null else TipOfTheDaySuppressor()
    }
  }

  init {
    val settings = GeneralSettings.getInstance()

    savedValue = settings.isShowTipsOnStartup
    settings.isShowTipsOnStartup = false
    LOGGER.info("Tip of the day has been disabled")
  }

  override fun dispose() {
    val settings = GeneralSettings.getInstance()

    if (!settings.isShowTipsOnStartup) { // nothing has been changed between init and dispose
      settings.isShowTipsOnStartup = savedValue
      LOGGER.info("Tip of the day has been enabled")
    }
    else {
      LOGGER.info("Tip of the day was enabled somewhere else")
    }
  }
}