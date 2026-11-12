// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext
import com.jetbrains.python.psi.resolve.fromModule
import com.jetbrains.python.psi.resolve.resolveQualifiedName

/**
 * Returns the module files of the pytest plugins that the installed distributions of [module] register.
 *
 * pytest reads the `pytest11` group of the `entry_points.txt` of each distribution, and it registers the object of each
 * entry point. The fixtures of that module, and the fixtures that it imports, are available to each test without an import.
 * [PyTestEntryPointIndex] keeps the entry points. The result is cached until a change of the PSI or of the roots.
 */
internal fun getPytestEntryPointPluginFiles(module: Module): List<PyFile> =
  CachedValuesManager.getManager(module.project).getCachedValue(module) {
    val context = fromModule(module)
    val pluginFiles = PyTestEntryPointIndex.getPytestPluginObjectReferences(GlobalSearchScope.moduleRuntimeScope(module, false))
      .flatMap { objectReference ->
        ProgressManager.checkCanceled()
        resolvePluginFiles(objectReference, context)
      }
      .distinct()
    CachedValueProvider.Result.create(pluginFiles,
                                      PsiModificationTracker.MODIFICATION_COUNT,
                                      ProjectRootModificationTracker.getInstance(module.project))
  }

/**
 * Resolves the [objectReference] of an entry point, as `module` or `module:attribute`, to the files of the plugin.
 *
 * pytest registers the attribute when the reference has one. An attribute that names a module, as `plugin` after
 * `from . import plugin`, gives that module. Any other attribute is an object in the module, so the module is the plugin file.
 */
private fun resolvePluginFiles(objectReference: String, context: PyQualifiedNameResolveContext): List<PyFile> {
  val moduleName = objectReference.substringBefore(':').trim().takeIf { it.isNotEmpty() } ?: return emptyList()
  val attribute = objectReference.substringAfter(':', "").trim()
  return resolveQualifiedName(QualifiedName.fromDottedString(moduleName), context).mapNotNull { asPyFile(it) }.map { moduleFile ->
    if (attribute.isEmpty()) moduleFile else resolveAttributeModule(moduleFile, attribute) ?: moduleFile
  }
}

private fun resolveAttributeModule(moduleFile: PyFile, attribute: String): PyFile? {
  val importElement = moduleFile.findExportedName(attribute) as? PyImportElement ?: return null
  return importElement.multiResolve().firstNotNullOfOrNull { asPyFile(it.element) }
}

private fun asPyFile(element: PsiElement?): PyFile? = (if (element is PsiDirectory) PyUtil.turnDirIntoInit(element) else element) as? PyFile
