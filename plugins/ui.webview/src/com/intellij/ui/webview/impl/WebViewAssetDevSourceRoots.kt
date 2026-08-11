// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node
import org.w3c.dom.NodeList

internal class WebViewAssetDevSourceRoots {
  private val cache = ConcurrentHashMap<WebViewAssetPath, DevSourceRootCacheValue>()

  fun find(source: WebViewAssetSource.Classpath, path: WebViewAssetPath): Path? {
    if (!isDevSourceFallbackEnabled()) return null

    source.devSourceRoot?.let { explicitRoot ->
      if (Files.isDirectory(explicitRoot)) return explicitRoot
      WebViewLogger.LOG.warn("Configured WebView asset dev source root does not exist: $explicitRoot")
    }

    return when (val cachedRoot = cache.computeIfAbsent(path) {
      findAutoDevSourceRoot(source, path)?.let { DevSourceRootCacheValue.Found(it) } ?: DevSourceRootCacheValue.Missing
    }) {
      is DevSourceRootCacheValue.Found -> cachedRoot.root
      DevSourceRootCacheValue.Missing -> null
    }
  }

  private fun isDevSourceFallbackEnabled(): Boolean {
    if (java.lang.Boolean.getBoolean("ide.webview.assets.disable.source.fallback")) return false
    if (java.lang.Boolean.getBoolean("ide.webview.assets.use.source.dir")) return true

    val application = runCatching { ApplicationManager.getApplication() }.getOrNull()
    return application?.isUnitTestMode == true ||
           AppMode.isRunningFromDevBuild() ||
           Files.isDirectory(Path.of(PathManager.getHomePath()).resolve(".idea"))
  }

  private fun findAutoDevSourceRoot(source: WebViewAssetSource.Classpath, path: WebViewAssetPath): Path? {
    val moduleName = moduleNameFor(source.owner) ?: return null
    val candidates = findModuleImlFiles(moduleName)
      .flatMap { parseResourceRoots(it).map { resourceRoot -> resourceRoot.resolve(source.root.path).normalize() } }
      .filter { Files.isRegularFile(it.resolve(path.path).normalize()) }
      .distinct()

    return when (candidates.size) {
      0 -> null
      1 -> candidates.single()
      else -> {
        WebViewLogger.LOG.warn("Ambiguous WebView asset source roots for module $moduleName, ${source.root}, and $path: $candidates")
        null
      }
    }
  }

  private fun moduleNameFor(owner: Class<*>): String? {
    val classRoot = PathManager.getJarForClass(owner) ?: return null
    return classRoot.fileName.toString()
      .removeSuffix(".jar")
      .removeSuffix("_test")
      .takeIf { it.isNotBlank() }
  }

  private fun findModuleImlFiles(moduleName: String): List<Path> {
    return moduleXmlFiles().flatMap { (projectDir, modulesXml) ->
      parseModuleFilePaths(projectDir, modulesXml)
    }.filter { it.fileName.toString() == "$moduleName.iml" }
  }

  private fun moduleXmlFiles(): List<Pair<Path, Path>> {
    val home = Path.of(PathManager.getHomePath()).toAbsolutePath().normalize()
    return sequenceOf(home, home.resolve("community"), home.parent?.resolve("community"))
      .filterNotNull()
      .distinct()
      .map { it to it.resolve(".idea/modules.xml") }
      .filter { Files.isRegularFile(it.second) }
      .toList()
  }

  private fun parseModuleFilePaths(projectDir: Path, modulesXml: Path): List<Path> {
    return parseXml(modulesXml).getElementsByTagName("module").asSequence()
      .mapNotNull { it.attributes?.getNamedItem("filepath")?.nodeValue }
      .mapNotNull { resolveMacroPath(it, projectDir, modulesXml.parent) }
      .toList()
  }

  private fun parseResourceRoots(imlFile: Path): List<Path> {
    val moduleDir = imlFile.parent
    return parseXml(imlFile).getElementsByTagName("sourceFolder").asSequence()
      .filter { node ->
        val type = node.attributes?.getNamedItem("type")?.nodeValue
        type == "java-resource" || type == "java-test-resource"
      }
      .mapNotNull { it.attributes?.getNamedItem("url")?.nodeValue }
      .mapNotNull { resolveMacroPath(it, moduleDir, moduleDir) }
      .filter { Files.isDirectory(it) }
      .toList()
  }

  @Suppress("HttpUrlsUsage")
  private fun parseXml(file: Path) = DocumentBuilderFactory.newInstance().apply {
    isExpandEntityReferences = false
    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
  }.newDocumentBuilder().let { builder ->
    Files.newInputStream(file).use { builder.parse(it) }
  }

  private fun resolveMacroPath(rawPath: String, projectDir: Path, moduleDir: Path?): Path? {
    var path = rawPath
      .replace("$" + "PROJECT_DIR$", projectDir.toString())
      .replace("$" + "MODULE_DIR$", moduleDir?.toString().orEmpty())
      .replace("$" + "USER_HOME$", System.getProperty("user.home"))
    if (path.startsWith("file://")) {
      path = path.removePrefix("file://")
    }
    return runCatching { Path.of(path).toAbsolutePath().normalize() }.getOrNull()
  }
}

private sealed class DevSourceRootCacheValue {
  data class Found(val root: Path) : DevSourceRootCacheValue()
  object Missing : DevSourceRootCacheValue()
}

private fun NodeList.asSequence(): Sequence<Node> {
  return (0 until length).asSequence().map { item(it) }
}
