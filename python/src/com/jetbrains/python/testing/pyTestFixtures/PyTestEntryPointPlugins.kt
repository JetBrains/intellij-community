// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.fromModule
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import org.ini4j.Ini
import java.io.IOException

/**
 * The entry point group that registers a pytest plugin.
 * pytest loads the module of each entry point of this group from the installed distributions.
 */
private const val PYTEST_PLUGIN_ENTRY_POINT_GROUP = "pytest11"

private const val ENTRY_POINTS_FILE = "entry_points.txt"

private val DISTRIBUTION_METADATA_SUFFIXES = listOf(".dist-info", ".egg-info")

/**
 * Returns the module files of the pytest plugins that the installed distributions of [module] register.
 *
 * pytest reads the `pytest11` group of the `entry_points.txt` of each distribution, and it loads the module of each entry point.
 * The fixtures of that module, and the fixtures that it imports, are available to each test without an import.
 */
internal fun getPytestEntryPointPluginFiles(module: Module): List<PyFile> {
  val context = fromModule(module)
  return getPytestEntryPointPluginModules(module).flatMap { moduleName ->
    resolveQualifiedName(moduleName, context).mapNotNull { element ->
      (if (element is PsiDirectory) PyUtil.turnDirIntoInit(element) else element) as? PyFile
    }
  }.distinct()
}

/**
 * Returns the qualified names of the modules in the `pytest11` entry points of the distributions in the class roots of [module].
 */
private fun getPytestEntryPointPluginModules(module: Module): List<QualifiedName> =
  CachedValuesManager.getManager(module.project).getCachedValue(module) {
    val moduleNames = OrderEnumerator.orderEntries(module).withoutModuleSourceEntries().classes().roots
      .asSequence()
      .flatMap { root -> root.children.asSequence() }
      .filter { child -> child.isDirectory && DISTRIBUTION_METADATA_SUFFIXES.any { child.name.endsWith(it) } }
      .mapNotNull { metadataDirectory -> metadataDirectory.findChild(ENTRY_POINTS_FILE) }
      .flatMap { entryPointsFile -> readPytestPluginModuleNames(entryPointsFile) }
      .distinct()
      .toList()
    CachedValueProvider.Result.create(moduleNames,
                                      ProjectRootModificationTracker.getInstance(module.project),
                                      VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
  }

/**
 * Reads the module names of the `pytest11` group from the [entryPointsFile] of a distribution.
 *
 * The value of an entry point is `module`, `module:attribute` or one of them with extras, as in `module:attribute [extra]`.
 */
private fun readPytestPluginModuleNames(entryPointsFile: VirtualFile): List<QualifiedName> {
  val ini = Ini().apply {
    config.isMultiOption = true
    config.isTree = false
    config.isEscape = false
    config.isEmptyOption = true
  }
  try {
    entryPointsFile.inputStream.use { ini.load(it) }
  }
  catch (e: IOException) {
    fileLogger().debug("Cannot read the entry points of ${entryPointsFile.path}", e)
    return emptyList()
  }
  val section = ini[PYTEST_PLUGIN_ENTRY_POINT_GROUP] ?: return emptyList()
  return section.keys.flatMap { name -> section.getAll(name).orEmpty() }.mapNotNull { value ->
    value?.substringBefore('[')?.substringBefore(':')?.trim()?.takeIf { it.isNotEmpty() }?.let(QualifiedName::fromDottedString)
  }
}
