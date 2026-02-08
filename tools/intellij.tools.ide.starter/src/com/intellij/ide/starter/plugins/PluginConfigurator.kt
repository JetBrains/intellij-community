package com.intellij.ide.starter.plugins

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.tools.ide.util.common.logOutput
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.walk
import kotlin.io.path.writeLines

class PluginNotFoundException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)

open class PluginConfigurator(val testContext: IDETestContext) {
  val disabledPluginsPath: Path
    get() = testContext.paths.configDir.resolve("disabled_plugins.txt")

  fun installPluginFromPath(pathToPluginArchive: Path): PluginConfigurator = apply {
    FileSystem.unpack(pathToPluginArchive, testContext.paths.pluginsDir)
  }

  fun installPluginFromDir(pathToPluginDir: Path): PluginConfigurator = apply {
    val targetPluginsDir = testContext.paths.pluginsDir
    val targetPluginDir = targetPluginsDir.resolve(pathToPluginDir.name)
    logOutput("Copy plugins from ${pathToPluginDir} to ${targetPluginDir}")

    if (targetPluginDir.exists()) {
      logOutput("Deleting the plugin directory from previous runs: ${targetPluginDir}")
      @OptIn(ExperimentalPathApi::class)
      targetPluginDir.deleteRecursively()
    }

    targetPluginDir.createDirectories()
    @OptIn(ExperimentalPathApi::class)
    pathToPluginDir.copyToRecursively(targetPluginDir, followLinks = false, overwrite = false)
  }

  @Deprecated("Use [installPluginFromDir] instead", level = DeprecationLevel.ERROR)
  @Suppress("unused")
  fun installPluginFromFolder(pathToPluginFolder: java.io.File): PluginConfigurator = installPluginFromDir(pathToPluginFolder.toPath())

  fun installPluginFromURL(urlToPluginZipFile: String): PluginConfigurator = apply {
    val pluginRootDir = GlobalPaths.instance.getCacheDirectoryFor("plugins")
    val pluginZip = pluginRootDir.resolve(testContext.ide.build).resolve(urlToPluginZipFile.substringAfterLast("/"))
    logOutput("Downloading $urlToPluginZipFile")

    try {
      HttpClient.download(urlToPluginZipFile, pluginZip)
    }
    catch (t: HttpClient.HttpNotFound) {
      throw PluginNotFoundException("Plugin $urlToPluginZipFile couldn't be downloaded: ${t.message}", t)
    }

    FileSystem.unpack(pluginZip, testContext.paths.pluginsDir)
  }

  fun installPluginFromPluginManager(
    pluginId: String,
    ide: InstalledIde,
    channel: String? = null,
    pluginFileName: String? = null,
  ): PluginConfigurator = installPluginFromPluginManager(PluginLatestForIde(pluginId, ide, channel, pluginFileName))

  fun installPluginFromPluginManager(
    pluginId: String,
    pluginVersion: String,
    channel: String? = null,
    pluginFileName: String? = null,
  ): PluginConfigurator = installPluginFromPluginManager(PluginWithExactVersion(pluginId, pluginVersion, channel, pluginFileName))

  fun installPluginFromPluginManager(
    plugin: PluginSourceDescriptor,
  ): PluginConfigurator = apply {
    val pluginId = plugin.pluginId
    logOutput("Setting up plugin: $pluginId ...")

    val pluginsCacheDir = GlobalPaths.instance.getCacheDirectoryFor("plugins")
    val fileName = plugin.pluginFileName ?: (pluginId.replace(".", "-") + ".zip")

    val downloadedPlugin = when (plugin) {
      is PluginLatestForIde -> pluginsCacheDir.resolve(plugin.ide.build).createDirectories().resolve(fileName)
      is PluginWithExactVersion -> pluginsCacheDir.resolve(plugin.version).createDirectories().resolve(fileName)
    }

    HttpClient.downloadIfMissing(plugin.downloadUrl(), downloadedPlugin, retries = 1)
    if (fileName.endsWith(".jar")) {
      Files.copy(downloadedPlugin, testContext.paths.pluginsDir.resolve(fileName))
    }
    else {
      FileSystem.unpack(downloadedPlugin, testContext.paths.pluginsDir)
    }

    logOutput("Plugin $pluginId setup finished")
  }

  fun disablePlugins(vararg pluginIds: String): PluginConfigurator = disablePlugins(pluginIds.toSet())

  fun disablePlugins(pluginIds: Set<String>): PluginConfigurator = apply {
    disabledPluginsPath.writeLines(disabledPluginIds + pluginIds)
  }

  fun enablePlugins(vararg pluginIds: String): PluginConfigurator = enablePlugins(pluginIds.toSet())

  private fun enablePlugins(pluginIds: Set<String>) = apply {
    disabledPluginsPath.writeLines(disabledPluginIds - pluginIds)
  }

  private val disabledPluginIds: Set<String>
    get() {
      val file = disabledPluginsPath
      return if (file.exists()) file.readLines().toSet() else emptySet()
    }

  private fun findPluginXmlByPluginIdInAGivenDir(pluginId: String, bundledPluginsDir: Path): Boolean = bundledPluginsDir.walk()
    .filter { it.extension == "jar" }
    .any { file ->
      val jarFile = JarFile(file.toString())
      return@any when (val entry = jarFile.getJarEntry("META-INF/plugin.xml")) {
        null -> false
        else -> jarFile.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }.contains("<id>$pluginId</id>")
      }
    }

  fun getPluginInstalledState(pluginId: String): PluginInstalledState {
    if (disabledPluginsPath.exists() && pluginId in disabledPluginIds) {
      return PluginInstalledState.DISABLED
    }

    val installedPluginDir = testContext.paths.pluginsDir
    if (findPluginXmlByPluginIdInAGivenDir(pluginId, installedPluginDir)) {
      return PluginInstalledState.INSTALLED
    }

    val bundledPluginsDir = testContext.ide.bundledPluginsDir
    if (bundledPluginsDir == null) {
      logOutput("Cannot ensure a plugin '$pluginId' is installed in ${testContext.ide}. Consider it is installed.")
      return PluginInstalledState.INSTALLED
    }

    if (findPluginXmlByPluginIdInAGivenDir(pluginId, bundledPluginsDir)) {
      return PluginInstalledState.BUNDLED_TO_IDE
    }

    return PluginInstalledState.NOT_INSTALLED
  }

  fun assertPluginIsInstalled(pluginId: String): PluginConfigurator = when (getPluginInstalledState(pluginId)) {
    PluginInstalledState.DISABLED -> error("Plugin '$pluginId' must not be listed in the disabled plugins file ${disabledPluginsPath}")
    PluginInstalledState.NOT_INSTALLED -> error("Plugin '$pluginId' must be installed")
    PluginInstalledState.BUNDLED_TO_IDE -> this
    PluginInstalledState.INSTALLED -> this
  }
}
