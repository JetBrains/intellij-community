// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin

import com.intellij.ide.ApplicationLoadHandler
import com.intellij.ide.BeforeApplicationLoadedEvent

internal class IntegrationTestApplicationLoadListener : ApplicationLoadHandler {
  data class Data(val projectPath: String, val args: List<String>)

  companion object {
    // Is there a better way to get command line args of application ?
    var data: Data? = null
  }

  override suspend fun beforeApplicationLoaded(event: BeforeApplicationLoadedEvent) {
    if (event.args.isEmpty()) return

    data = Data(event.args.first(), event.args)
  }
}