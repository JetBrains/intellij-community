package com.intellij.tools.ide.starter.build.server

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer
import com.intellij.ide.starter.di.DISnapshot
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.DevBuildServerRunner
import org.junit.platform.launcher.TestExecutionListener
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.net.URI

private val lock = Any()

fun initStarterDevBuildServerDI() {
  synchronized(lock) {
    di = DI {
      extend(di)
      bindSingleton<DevBuildServerRunner>(overrides = true) { DevBuildServerRunnerImpl }

      // TODO: remove this temporary workaround in case if we will announce lambda tests for external contributors/plugin developers
      bindSingleton(tag = "teamcity.uri", overrides = true) { URI("https://buildserver.labs.intellij.net").normalize() }
      bindSingleton<CIServer>(overrides = true) { TeamCityCIServer() }
    }
    DISnapshot.initSnapshot(di, overwrite = true)
  }
}

class DevBuildServerListener : TestExecutionListener {
  companion object {
    init {
      initStarterDevBuildServerDI()
    }
  }
}