// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Converted from Java. Notes on the conversion:
//  - Static helpers became top-level functions: extension classes registered through extension
//    points may not carry non-trivial companion objects (lint rule), so they cannot live on the class.
//  - File I/O migrated from `java.io.File` to `java.nio.file.Path`
package com.jetbrains.python.sdk.skeletons

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.ZipUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PythonEnvironment
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pythonInterpreter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.Path
import java.util.regex.PatternSyntaxException
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private val LOG = Logger.getInstance(DefaultPregeneratedSkeletonsProvider::class.java)

internal class DefaultPregeneratedSkeletonsProvider : PyPregeneratedSkeletonsProvider {

  override fun getSkeletonsForSdk(sdk: Sdk, generatorVersion: Int): PyPregeneratedSkeletons? {
    val root = findPregeneratedSkeletonsRoot()
    if (root == null || !root.exists()) {
      return null
    }
    LOG.info("Pregenerated skeletons root is $root")

    val prebuiltSkeletonsName = getPregeneratedSkeletonsName(
      sdk, generatorVersion, Registry.`is`("python.prebuilt.skeletons.minor.version.aware"), false
    ) ?: return null

    val f = root.listDirectoryEntries().firstOrNull { isApplicableZippedSkeletonsFileName(prebuiltSkeletonsName, it.name) }
    if (f == null) {
      LOG.info("Not found pre-generated skeletons at $root")
      return null
    }
    LOG.info("Found pre-generated skeletons at $f")
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(f)
    if (virtualFile == null) {
      LOG.info("Could not find pre-generated skeletons in VFS")
      return null
    }
    val archiveRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile)
    if (archiveRoot == null) {
      LOG.info("Could not resolve jar root for ${virtualFile.path}")
      return null
    }
    return ArchivedSkeletons(archiveRoot)
  }
}

private fun findPregeneratedSkeletonsRoot(): Path? {
  val home = Path.of(PathManager.getHomePath())
  LOG.info("Home path is $home")
  val fromSources = home.resolve("python/skeletons")
  if (fromSources.exists()) return fromSources
  val compiled = home.resolve("skeletons")
  if (compiled.exists()) return compiled
  return null
}

@VisibleForTesting
fun isApplicableZippedSkeletonsFileName(prebuiltSkeletonsName: String, fileName: String): Boolean = try {
  fileName.matches(Regex(".*$prebuiltSkeletonsName\\.?\\d*\\.zip"))
}
catch (_: PatternSyntaxException) {
  false
}

@ApiStatus.Internal
fun getPregeneratedSkeletonsName(
  sdk: Sdk,
  generatorVersion: Int,
  withMinorVersion: Boolean,
  withExtension: Boolean,
): String? {
  if (PythonSdkUtil.isRemote(sdk)) return null
  @NonNls val versionString = sdk.versionString ?: return null
  val rich = sdk.pythonInterpreter(false)
  val effectiveVersion = when (rich.pythonEnvironment) {
    is PythonEnvironment.Conda -> "Anaconda-$versionString"
    is PythonEnvironment.Venv, is PythonEnvironment.SystemPython, null -> versionString
  }
  return getPrebuiltSkeletonsName(generatorVersion, effectiveVersion, withMinorVersion, withExtension)
}

@ApiStatus.Internal
@VisibleForTesting
fun getPrebuiltSkeletonsName(
  generatorVersion: Int,
  @NonNls versionString: String,
  withMinorVersion: Boolean,
  withExtension: Boolean,
): String {
  var version = StringUtil.toLowerCase(versionString).replace(" ", "-")
  if (!withMinorVersion) {
    val ind = version.lastIndexOf(".")
    if (ind != -1) {
      version = version.substring(0, ind)
    }
  }

  val extension = if (withExtension) ".zip" else ""
  return if (SystemInfo.isMac) {
    var osVersion = SystemInfo.OS_VERSION
    val dot = osVersion.indexOf('.')
    if (dot >= 0) {
      val secondDot = osVersion.indexOf('.', dot + 1)
      if (secondDot >= 0) {
        osVersion = osVersion.substring(0, secondDot)
      }
    }
    "skeletons-mac-$generatorVersion-$osVersion-$version$extension"
  }
  else {
    val os = if (SystemInfo.isWindows) "win" else "nix"
    "skeletons-$os-$generatorVersion-$version$extension"
  }
}

private class ArchivedSkeletons(private val archiveRoot: VirtualFile) : PyPregeneratedSkeletons {
  override fun unpackPreGeneratedSkeletons(skeletonDir: Path) {
    ProgressManager.progress(PyBundle.message("python.sdk.unpacking.pre.generated.skeletons"))
    try {
      val jar = JarFileSystem.getInstance().getVirtualFileForJar(archiveRoot)
      if (jar != null) {
        ZipUtil.extract(Path.of(jar.path), skeletonDir, null)
      }
    }
    catch (e: IOException) {
      LOG.info("Error unpacking pre-generated skeletons", e)
    }
  }
}
