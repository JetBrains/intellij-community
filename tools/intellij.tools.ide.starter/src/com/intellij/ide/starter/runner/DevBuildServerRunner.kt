package com.intellij.ide.starter.runner

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path

interface DevBuildServerRunner {
  fun isDevBuildSupported(): Boolean

  suspend fun readVmOptions(installationDirectory: Path): List<String>
  suspend fun startDevBuild(ideInfo: IdeInfo): Path

  companion object {
    val instance: DevBuildServerRunner
      get() = di.direct.instance<DevBuildServerRunner>()
  }
}

object NoOpDevBuildServerRunner : DevBuildServerRunner {
  override fun isDevBuildSupported(): Boolean = false
  override suspend fun readVmOptions(installationDirectory: Path): List<String> = error("Reading VM options isn't supported.")
  override suspend fun startDevBuild(ideInfo: IdeInfo): Path =
    error("Starting dev build isn't supported. Add dependency on intellij.tools.ide.starter.build.server module.")
}