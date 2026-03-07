// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin

import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.application.Application
import java.nio.file.Path

class IntegrationTestApplicationLoadListener : ApplicationLoadListener {
  data class Data(val projectPath: String, val args: List<String>)

  companion object {
    // Is there a better way to get command line args of application ?
    var data: Data? = null
  }

  override suspend fun beforeApplicationLoaded(application: Application, configPath: Path, args: List<String>) {
    if (args.isEmpty()) return
    else data = Data(args.first(), args)

    super.beforeApplicationLoaded(application, configPath, args)
  }

  override suspend fun beforeApplicationLoaded(application: Application, configPath: Path) {
  }
}