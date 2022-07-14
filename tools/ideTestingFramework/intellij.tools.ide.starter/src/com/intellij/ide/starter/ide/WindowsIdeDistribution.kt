package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.utils.callJavaVersion
import com.intellij.ide.starter.utils.logOutput
import java.nio.file.Path
import kotlin.io.path.*

class WindowsIdeDistribution : IdeDistribution() {
  override fun installIde(unpackDir: Path, executableFileName: String): InstalledIde {
    val buildTxtPath = unpackDir.resolve("build.txt")
    require(buildTxtPath.isRegularFile()) { "Cannot find WindowsOS IDE vmoptions file in $unpackDir" }
    val (productCode, build) = buildTxtPath.readText().trim().split("-", limit = 2)

    val binDir = unpackDir / "bin"

    val allBinFiles = binDir.listDirectoryEntries()

    val executablePath = allBinFiles.singleOrNull { file ->
      file.fileName.toString() == "${executableFileName}64.exe"
    } ?: error("Failed to detect executable name, ending with 64.exe in:\n${allBinFiles.joinToString("\n")}")

    val originalVMOptionsFile = executablePath.parent.resolve("${executablePath.fileName}.vmoptions")

    return object : InstalledIde {
      override val bundledPluginsDir = unpackDir.resolve("plugins")

      override val originalVMOptions = VMOptions.readIdeVMOptions(this, originalVMOptionsFile)
      override val patchedVMOptionsFile = unpackDir.parent.resolve("${unpackDir.fileName}.vmoptions")

      override fun startConfig(vmOptions: VMOptions, logsDir: Path) = object : InstalledBackedIDEStartConfig(patchedVMOptionsFile,
                                                                                                             vmOptions) {
        override val workDir = unpackDir
        override val commandLine = listOf(executablePath.toAbsolutePath().toString())
      }

      override val build = build
      override val os = "windows"
      override val productCode = productCode
      override val isFromSources = false

      override fun toString() = "IDE{$productCode, $build, $os, home=$unpackDir}"
      override fun resolveAndDownloadTheSameJDK(): Path {
        val jbrHome = unpackDir / "jbr"
        require(jbrHome.isDirectory()) {
          "JbrHome is not found under $jbrHome"
        }

        val jbrFullVersion = callJavaVersion(jbrHome).substringAfter("build ").substringBefore(")")
        logOutput("Found following $jbrFullVersion in the product: $productCode $build")

        // in Android Studio bundled only JRE
        if (productCode == IdeProductProvider.AI.productCode) return jbrHome
        return downloadAndUnpackJbrIfNeeded(jbrFullVersion)
      }
    }
  }
}