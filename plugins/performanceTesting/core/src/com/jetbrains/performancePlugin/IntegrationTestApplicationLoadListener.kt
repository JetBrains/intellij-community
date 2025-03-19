package com.jetbrains.performancePlugin

import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.application.Application
import java.nio.file.Path

class IntegrationTestApplicationLoadListener : ApplicationLoadListener {
  companion object {
    // Is there a better way to get command line args of application ?
    var projectPathFromCommandLine: String? = null
  }

  override suspend fun beforeApplicationLoaded(application: Application, configPath: Path, args: List<String>) {
    if (args.isEmpty()) return
    else projectPathFromCommandLine = args.first()

    super.beforeApplicationLoaded(application, configPath, args)
  }

  override suspend fun beforeApplicationLoaded(application: Application, configPath: Path) {
  }
}