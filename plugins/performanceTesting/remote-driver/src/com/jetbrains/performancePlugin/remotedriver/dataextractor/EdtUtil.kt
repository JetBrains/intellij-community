// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.performancePlugin.remotedriver.dataextractor

import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.edt.GuiQuery

internal fun <ReturnType> computeOnEdt(query: () -> ReturnType): ReturnType = GuiActionRunner.execute(object : GuiQuery<ReturnType>() {
  override fun executeInEDT(): ReturnType = query()
})