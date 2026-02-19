package com.intellij.ide.starter.ide

import com.intellij.openapi.util.SystemInfo
import java.io.File

object DefaultIdeDistributionFactory : IdeDistributionFactory {
  override fun installIDE(unpackDir: File, executableFileName: String): InstalledIde {
    val distribution = when {
      SystemInfo.isMac -> MacOsIdeDistribution()
      SystemInfo.isWindows -> WindowsIdeDistribution()
      SystemInfo.isLinux -> LinuxIdeDistribution()
      else -> error("Not supported ide distribution: $unpackDir")
    }

    return distribution.installIde(unpackDir.toPath(), executableFileName)
  }
}

interface IdeDistributionFactory {
  fun installIDE(unpackDir: File, executableFileName: String): InstalledIde
}