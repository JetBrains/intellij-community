// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typeRepresentation

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typeRepresentation.psi.PyTypeRepresentationFile
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.stubs.PyModuleNameIndex
import com.jetbrains.python.psi.types.TypeEvalContext

private const val UNKNOWN_MODULE = "__unknown__"

/**
 * Resolves a qualified name from a type representation, first from the import roots, then by the type engine's module name.
 * A result that is not a [T] does not stop the fallback.
 */
internal inline fun <reified T : PsiElement> resolveTypeRepresentationName(
  qualifiedName: QualifiedName,
  anchor: PsiElement,
  context: TypeEvalContext,
): T? =
  PyResolveUtil.resolveFullyQualifiedName(qualifiedName, anchor, context) as? T
  ?: FileContextUtil.getContextFile(anchor)?.let { resolveByTypeEngineModuleName(qualifiedName, it, context) } as? T

/**
 * Resolves [qualifiedName] in the project file whose path ends with its module part.
 *
 * A type engine names a module by its own import roots, which need not match the project's: Pyrefly with no config names `src/sample.py`
 * `sample`, and a file off its search path `__unknown__`, which can only be [contextFile] itself. Among several files that match, the one
 * the type was requested for wins, then one in its directory; otherwise the match must be unique.
 */
internal fun resolveByTypeEngineModuleName(qualifiedName: QualifiedName, contextFile: PsiFile, context: TypeEvalContext): PsiElement? {
  // The original of a completion or preview copy, so that its PSI does not end up among the types of the physical file
  val file = (contextFile.originalFile as? PyFile)?.takeUnless { it is PyTypeRepresentationFile }
  if (qualifiedName.firstComponent == UNKNOWN_MODULE) {
    return file?.let { PyResolveUtil.resolveMemberPath(it, qualifiedName.removeHead(1), context) }
  }
  return (qualifiedName.componentCount - 1 downTo 1).firstNotNullOfOrNull { moduleLength ->
    findModule(qualifiedName.subQualifiedName(0, moduleLength), file, contextFile.project)
      ?.let { PyResolveUtil.resolveMemberPath(it, qualifiedName.removeHead(moduleLength), context) }
  }
}

private fun findModule(moduleName: QualifiedName, contextFile: PyFile?, project: Project): PyFile? {
  if (contextFile != null && contextFile.hasModulePath(moduleName)) return contextFile
  val shortName = moduleName.lastComponent
  if (shortName == null || DumbService.isDumb(project)) return null
  val modules = PyModuleNameIndex.findByShortName(shortName, project, GlobalSearchScope.projectScope(project))
    .filter { it.hasModulePath(moduleName) }
  return modules.singleOrNull() ?: modules.singleOrNull { it.containingDirectory == contextFile?.containingDirectory }
}

/** Whether the file's path, `__init__` dropped, ends with [moduleName], e.g. `src/x/n.py` with `n` or `x.n`. */
private fun PyFile.hasModulePath(moduleName: QualifiedName): Boolean {
  val stem = name.substringBeforeLast('.')
  val directories = generateSequence(virtualFile?.parent) { it.parent }.map { it.name }
  val path = if (stem == PyNames.INIT) directories else sequenceOf(stem) + directories
  return path.take(moduleName.componentCount).toList() == moduleName.components.asReversed()
}
