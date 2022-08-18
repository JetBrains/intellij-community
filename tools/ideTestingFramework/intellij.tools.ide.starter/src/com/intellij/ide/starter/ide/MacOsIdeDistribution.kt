package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.utils.callJavaVersion
import com.intellij.ide.starter.utils.logOutput
import org.w3c.dom.Node
import java.io.File
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class MacOsIdeDistribution : IdeDistribution() {

  private fun getExecutableNameFromInfoPlist(appDir: File, @Suppress("SameParameterValue") keyName: String): String {
    val infoPlistFile = appDir.resolve("Contents/Info.plist")
    val xmlFactory = DocumentBuilderFactory.newInstance()

    infoPlistFile.inputStream().use {
      val xmlBuilder = xmlFactory.newDocumentBuilder()
      val document = xmlBuilder.parse(it)

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
    val appDir = unpackDir.toFile().listFiles()?.singleOrNull { it.name.endsWith(".app") }?.toPath()
                 ?: error("Invalid macOS application directory: $unpackDir")

    val executableName = getExecutableNameFromInfoPlist(appDir.toFile(), "CFBundleExecutable")

    val appHome = appDir.resolve("Contents")
    val executablePath = appHome / "MacOS" / executableName
    require(executablePath.isRegularFile()) { "Cannot find macOS IDE executable file in $executablePath" }

    val originalVMOptions = appHome / "bin" / "$executableName.vmoptions"
    require(originalVMOptions.isRegularFile()) { "Cannot find macOS IDE vmoptions file in $executablePath" }

    val buildTxtPath = appHome / "Resources" / "build.txt"
    require(buildTxtPath.isRegularFile()) { "Cannot find macOS IDE vmoptions file in $executablePath" }

    val (productCode, build) = buildTxtPath.readText().trim().split("-", limit = 2)

    return object : InstalledIde {
      override val bundledPluginsDir = appHome / "plugins"

      override val originalVMOptions = VMOptions.readIdeVMOptions(this, originalVMOptions)
      override val patchedVMOptionsFile = appDir.parent.resolve("${appDir.fileName}.vmoptions") //see IDEA-220286

      override fun startConfig(vmOptions: VMOptions, logsDir: Path) = object : InstalledBackedIDEStartConfig(patchedVMOptionsFile,
                                                                                                             vmOptions) {
        override val workDir = appDir
        override val commandLine = listOf(executablePath.toAbsolutePath().toString())
      }

      override val build = build
      override val os = "mac"
      override val productCode = productCode
      override val isFromSources = false

      override fun toString() = "IDE{$productCode, $build, $os, home=$appDir}"

      override fun resolveAndDownloadTheSameJDK(): Path {
        val jbrHome = appHome / "jbr"

        require(jbrHome.isDirectory()) {
          "JbrHome is not found under $jbrHome"
        }

        val javaHome = jbrHome / "Contents" / "Home"
        require(javaHome.isDirectory()) {
          "JavaHome is not found under $javaHome"
        }

        val jbrFullVersion = callJavaVersion(javaHome).substringAfter("build ").substringBefore(")")
        logOutput("Found following $jbrFullVersion in the product: $productCode $build")

        // in Android Studio bundled only JRE
        if (productCode == IdeProductProvider.AI.productCode) return jbrHome
        return downloadAndUnpackJbrIfNeeded(jbrFullVersion)
      }
    }
  }
}