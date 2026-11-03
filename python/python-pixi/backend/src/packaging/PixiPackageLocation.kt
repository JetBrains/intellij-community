// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.equalsMayBeUsingMachine
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pixi.backend.workspace.parsePixiToml
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText

internal data class PixiPackageLocation(
  val name: String,
  val arguments: List<String>,
  val isPathDependency: Boolean = false,
  val sourcePath: Path? = null,
)

internal fun PixiPackageLocation.relativeToWorkspace(workspaceRoot: Path): PyResult<PixiPackageLocation> {
  val source = sourcePath?.toAbsolutePath()?.normalize() ?: return PyResult.success(this)
  val root = workspaceRoot.toAbsolutePath().normalize()
  if (!source.startsWith(root)) {
    return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.location.target.outside.workspace"))
  }
  val relativePath = "./" + root.relativize(source).joinToString("/")
  val relativeArguments = if ("--path" in arguments) listOf("--path", relativePath, "--", name)
  else listOf("--", "$name @ $relativePath")
  return PyResult.success(copy(arguments = relativeArguments))
}

internal suspend fun resolvePixiPackageLocation(
  location: URI,
  owner: Path,
  resolveSourceName: suspend (URI) -> PyResult<String?> = { PyResult.success(null) },
): PyResult<PixiPackageLocation> = withContext(Dispatchers.IO) {
  fun unsupported(): PyResult<PixiPackageLocation> =
    PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.location.unsupported"))
  if (location.scheme != "file") {
    if (location.scheme !in setOf("https", "http", "git+https", "git+ssh")) return@withContext unsupported()
    val name = location.fragment?.substringAfter("egg=", "")?.substringBefore('&')?.takeIf { it.isNotEmpty() }
               ?: wheelDistributionName(location.path.orEmpty().substringAfterLast('/'))
               ?: resolveSourceName(location).getOr { return@withContext it }
               ?: distributionName(location.path.orEmpty().substringAfterLast('/'))
    if (name == null || !PACKAGE_NAME.matches(name)) return@withContext unsupported()
    return@withContext PyResult.success(PixiPackageLocation(name, listOf("--", "$name @ ${location.toASCIIString()}")))
  }
  val path = try {
    Path.of(location)
  }
  catch (_: IllegalArgumentException) {
    return@withContext unsupported()
  }
  if (!path.getEelDescriptor().equalsMayBeUsingMachine(owner.getEelDescriptor())) return@withContext unsupported()
  val name = try {
    if (path.isDirectory()) {
      val manifest = path.resolve("pyproject.toml")
      if (manifest.exists()) {
        val table = parsePixiToml(manifest.readText()) ?: return@withContext unsupported()
        table.get("project.name") as? String
      }
      else null
    }
    else wheelDistributionName(path.name)
  }
  catch (_: IOException) {
    return@withContext unsupported()
  }
  val resolvedName = name ?: resolveSourceName(location).getOr { return@withContext it }
                     ?: distributionName(path.name).takeUnless { path.isDirectory() }
  if (resolvedName == null || !PACKAGE_NAME.matches(resolvedName)) return@withContext unsupported()
  val isPathDependency = path.isDirectory()
  val arguments = if (isPathDependency && name != null) listOf("--path", path.asEelPath().toString(), "--", resolvedName)
  else listOf("--", "$resolvedName @ ${path.toUri().toASCIIString()}")
  PyResult.success(PixiPackageLocation(resolvedName, arguments, isPathDependency, path))
}

private val PACKAGE_NAME = Regex("[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?")

private fun wheelDistributionName(fileName: String): String? {
  if (!fileName.endsWith(".whl")) return null
  val fields = fileName.removeSuffix(".whl").split('-')
  return fields.takeIf { it.size in 5..6 && it.all(String::isNotEmpty) }?.first()?.takeIf(PACKAGE_NAME::matches)
}

private fun distributionName(fileName: String): String? {
  wheelDistributionName(fileName)?.let { return it }
  val suffix = listOf(".tar.gz", ".zip").firstOrNull(fileName::endsWith) ?: return null
  val stem = fileName.removeSuffix(suffix)
  val name = stem.substringBeforeLast('-', "")
  val version = stem.substringAfterLast('-')
  return name.takeIf { PACKAGE_NAME.matches(it) && version.firstOrNull()?.isDigit() == true }
}
