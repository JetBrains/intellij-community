package com.intellij.ide.starter.ide

import com.intellij.util.system.OS
import java.nio.file.Path

object DefaultIdeDistributionFactory : IdeDistributionFactory {
  override fun installIDE(unpackDir: Path, executableFileName: String): InstalledIde {
    val distribution = when (OS.CURRENT) {
      OS.macOS -> MacOsIdeDistribution()
      OS.Windows -> WindowsIdeDistribution()
      OS.Linux -> LinuxIdeDistribution()
      else -> error("Not supported ide distribution: $unpackDir")
    }

    return distribution.installIde(unpackDir, executableFileName)
  }
}

interface IdeDistributionFactory {
  fun installIDE(unpackDir: Path, executableFileName: String): InstalledIde
}