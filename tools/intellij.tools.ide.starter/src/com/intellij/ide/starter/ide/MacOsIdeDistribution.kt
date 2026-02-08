package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.utils.FileSystem.listDirectoryEntriesQuietly
import com.intellij.ide.starter.utils.JvmUtils
import com.intellij.ide.starter.utils.XmlBuilder
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import org.w3c.dom.Node
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

class MacOsIdeDistribution : IdeDistribution() {

  private fun getExecutableNameFromInfoPlist(appDir: Path, @Suppress("SameParameterValue") keyName: String): String {
    val infoPlistFile = appDir.resolve("Contents/Info.plist")

    infoPlistFile.inputStream().use {
      val document = XmlBuilder.parse(it)

      val keys = document.getElementsByTagName("key")

      for (index in 0 until keys.length) {
        val keyItem = keys.item(index)

        // found the node we are looking for
        if (keyItem.firstChild.nodeValue == keyName) {

          // lets find the value - it will be the next sibling
          var sibling: Node = keyItem.nextSibling
          while (sibling.nodeType != Node.ELEMENT_NODE) {
            sibling = sibling.nextSibling
          }

          return sibling.textContent
        }
      }
    }

    error("Failed to resolve key: $keyName in $infoPlistFile")
  }

  override fun installIde(unpackDir: Path, executableFileName: String): InstalledIde {
    val appDir = unpackDir.listDirectoryEntriesQuietly()?.singleOrNull { it.name.endsWith(".app") }?.toAbsolutePath()
                 ?: error("Invalid macOS application directory: $unpackDir")

    val executableName = getExecutableNameFromInfoPlist(appDir, "CFBundleExecutable")

    val appHome = appDir.resolve("Contents").toAbsolutePath()
    val executablePath = appHome / "MacOS" / executableName
    require(executablePath.isRegularFile()) { "Cannot find macOS IDE executable file in $executablePath" }

    val (productCode, build) = readProductCodeAndBuildNumberFromBuildTxt(appHome / "Resources" / "build.txt")

    return object : InstalledIde {
      override val bundledPluginsDir = appHome / "plugins"

      private val vmOptionsFinal: VMOptions = VMOptions(
        ide = this,
        data = emptyList(),
        env = emptyMap()
      )

      override val vmOptions: VMOptions
        get() = vmOptionsFinal

      override val patchedVMOptionsFile = appDir.parent.resolve("${appDir.fileName}.vmoptions") //see IDEA-220286

      override fun startConfig(vmOptions: VMOptions, logsDir: Path) = object : InstalledBackedIDEStartConfig(patchedVMOptionsFile,
                                                                                                             vmOptions) {
        override val workDir = appDir
        override val commandLine = listOf(executablePath.toAbsolutePath().toString())
      }

      override val build = build
      override val os = OS.macOS
      override val productCode = productCode
      override val isFromSources = false
      override val installationPath: Path = appHome

      override fun toString() = "IDE{$productCode, $build, $os, home=$appDir}"

      override suspend fun resolveAndDownloadTheSameJDK(): Path {
        val jbrHome = appHome / "jbr"

        require(jbrHome.isDirectory()) {
          "JbrHome is not found under $jbrHome"
        }

        val javaHome = jbrHome / "Contents" / "Home"
        require(javaHome.isDirectory()) {
          "JavaHome is not found under $javaHome"
        }

        val jbrFullVersion = JvmUtils.callJavaVersion(javaHome).substringAfter("build ").substringBefore(")")
        logOutput("Found following $jbrFullVersion in the product: $productCode $build")

        return javaHome
      }
    }
  }
}