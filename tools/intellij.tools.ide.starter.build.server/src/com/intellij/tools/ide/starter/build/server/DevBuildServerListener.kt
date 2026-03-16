package com.intellij.tools.ide.starter.build.server

import com.intellij.ide.starter.di.DISnapshot
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.DevBuildServerRunner
import org.junit.platform.launcher.TestExecutionListener
import org.kodein.di.DI
import org.kodein.di.bindSingleton

fun initStarterDevBuildServerDI() {
  di = DI {
    extend(di)
    bindSingleton<DevBuildServerRunner>(overrides = true) { DevBuildServerRunnerImpl }
  }
  DISnapshot.initSnapshot(di, overwrite = true)
}

class DevBuildServerListener : TestExecutionListener {
  companion object {
    init {
      initStarterDevBuildServerDI()
    }
  }
}