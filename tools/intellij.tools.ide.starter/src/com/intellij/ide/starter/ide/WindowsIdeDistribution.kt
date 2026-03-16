package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.utils.FileSystem.listDirectoryEntriesQuietly
import com.intellij.ide.starter.utils.JvmUtils
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

class WindowsIdeDistribution : IdeDistribution() {
  override fun installIde(unpackDir: Path, executableFileName: String): InstalledIde {
    val appHome = (unpackDir.listDirectoryEntriesQuietly()?.singleOrNull { it.isDirectory() } ?: unpackDir).toAbsolutePath()
    val (productCode, build) = readProductCodeAndBuildNumberFromBuildTxt(appHome.resolve("build.txt"))

    val binDir = appHome / "bin"

    val allBinFiles = binDir.listDirectoryEntries()

    val executablePath = allBinFiles.singleOrNull { file ->
      file.fileName.toString() == "${executableFileName}64.exe"
    } ?: error("Failed to detect executable ${executableFileName}64.exe:\n${allBinFiles.joinToString("\n")}")

    return object : InstalledIde {
      override val bundledPluginsDir = appHome.resolve("plugins")

      private val vmOptionsFinal: VMOptions = VMOptions(
        ide = this,
        data = emptyList(),
        env = emptyMap()
      )

      override val vmOptions: VMOptions
        get() = vmOptionsFinal

      override val patchedVMOptionsFile = appHome.parent.resolve("${appHome.fileName}.vmoptions")

      override fun startConfig(vmOptions: VMOptions, logsDir: Path) = object : InstalledBackedIDEStartConfig(patchedVMOptionsFile,
                                                                                                             vmOptions) {
        override val workDir = appHome
        override val commandLine = listOf(executablePath.toAbsolutePath().toString())
      }

      override val build = build
      override val os = OS.Windows
      override val productCode = productCode
      override val isFromSources = false
      override val installationPath: Path = appHome.toAbsolutePath()

      override fun toString() = "IDE{$productCode, $build, $os, home=$unpackDir}"

      override suspend fun resolveAndDownloadTheSameJDK(): Path {
        val jbrHome = appHome / "jbr"
        require(jbrHome.isDirectory()) {
          "JbrHome is not found under $jbrHome"
        }

        val jbrFullVersion = JvmUtils.callJavaVersion(jbrHome).substringAfter("build ").substringBefore(")")
        logOutput("Found following $jbrFullVersion in the product: $productCode $build")

        return jbrHome
      }
    }
  }
}