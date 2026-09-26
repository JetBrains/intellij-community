package com.intellij.tools.ide.starter.build.server

import com.intellij.ide.starter.di.DISnapshot
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.DevBuildServerRunner
import com.intellij.platform.devIdeConfig.DevIdeConfig
import com.intellij.tools.ide.util.common.logOutput
import org.junit.platform.launcher.TestExecutionListener
import org.kodein.di.DI
import org.kodein.di.bindSingleton

fun initStarterDevBuildServerDI() {
  val runner = devBuildServerRunner()
  di = DI {
    extend(di)
    bindSingleton<DevBuildServerRunner>(overrides = true) { runner }
  }
  DISnapshot.initSnapshot(di, overwrite = true)
}

/**
 * Assembling the IDE in this process is the default; a test target that declares an already-assembled distribution gets
 * it instead.
 *
 * The choice is made here, in the one place that binds the runner, rather than by a second module contributing a second
 * [TestExecutionListener]: service-loader order is unspecified, so two listeners overriding the same binding could
 * silently restore the assembling runner and spend minutes building an IDE the test target already had.
 */
private fun devBuildServerRunner(): DevBuildServerRunner {
  val configFile = DevIdeConfig.declaredConfigFile() ?: return DevBuildServerRunnerImpl
  logOutput("Dev build server: using the distribution declared by '${DevIdeConfig.CONFIG_PATH_PROPERTY}' ($configFile)")
  return PrebuiltDevDistRunner(configFile)
}

class DevBuildServerListener : TestExecutionListener {
  companion object {
    init {
      initStarterDevBuildServerDI()
    }
  }
}
