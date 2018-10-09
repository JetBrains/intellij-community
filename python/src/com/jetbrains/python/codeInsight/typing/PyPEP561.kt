// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PyPEP561")

package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext
import com.jetbrains.python.psi.resolve.resolveModuleAt
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.sdk.PythonSdkType

private const val STUBS_SUFFIX = "-stubs"
private val STUB_PACKAGE_KEY = Key<Boolean>("PY_STUB_PACKAGE")
private val INLINE_PACKAGE_KEY = Key<Boolean>("PY_INLINE_PACKAGE")
private val PEP_561_KEY = Key<Boolean>("PY_PEP_561_KEY")

/**
 * If [name] argument points to element in stub package,
 * then [name] would be copied and `-stubs` suffix would be removed from the first component,
 * otherwise [name] would be returned.
 */
fun convertStubToRuntimePackageName(name: QualifiedName): QualifiedName {
  val top = name.firstComponent

  if (top != null && top.endsWith(STUBS_SUFFIX)) {
    return QualifiedName.fromComponents(name.components).apply { components[0] = components[0].dropLast(STUBS_SUFFIX.length) }
  }

  return name
}

/**
 * Resolves [name] in corresponding stub package.
 *
 * Returns empty list if [context] disallow stubs,
 * or language level is older than [LanguageLevel.PYTHON37],
 * or [item] is not lib root.
 */
fun resolveModuleAtStubPackage(name: QualifiedName,
                               item: PsiFileSystemItem,
                               context: PyQualifiedNameResolveContext): List<PsiElement> {
  if (!context.withoutStubs && name.componentCount > 0) {
    val head = name.firstComponent!!

    // prevent recursion and check that stub packages are allowed
    if (!head.endsWith(STUBS_SUFFIX) && contextLanguageLevel(context).isAtLeast(LanguageLevel.PYTHON37)) {
      val virtualFile = item.virtualFile

      // check that resolve is running from lib root
      if (virtualFile != null && virtualFile == ProjectFileIndex.getInstance(context.project).getClassRootForFile(virtualFile)) {
        val nameInStubPackage = sequenceOf("$head$STUBS_SUFFIX") + name.components.asSequence().drop(1)
        return resolveModuleAt(QualifiedName.fromComponents(nameInStubPackage.toList()), item, context)
          .asSequence()
          .filter(::pyi)
          .onEach { it.putUserData(STUB_PACKAGE_KEY, true) }
          .toList()
      }
    }
  }

  return emptyList()
}

/**
 * Filters resolved elements according to their import priority in sys.path and
 * [PEP 561](https://www.python.org/dev/peps/pep-0561/#type-checker-module-resolution-order) rules.
 */
fun filterTopPriorityResults(resolved: List<PsiElement>, module: Module?): List<PsiElement> =
  if (resolved.all(::isNamespacePackage)) resolved
  else listOfNotNull(resolved.maxBy { resolvedElementPriority(it, module) })

private fun contextLanguageLevel(context: PyQualifiedNameResolveContext): LanguageLevel {
  context.foothold?.also { return LanguageLevel.forElement(it) }
  context.footholdFile?.also { return LanguageLevel.forElement(it) }

  context.sdk?.also { return PythonSdkType.getLanguageLevelForSdk(it) }
  context.effectiveSdk?.also { return PythonSdkType.getLanguageLevelForSdk(it) }

  context.module?.also { return PyUtil.getLanguageLevelForModule(it) }

  return LanguageLevel.getDefault()
}

private fun pyi(element: PsiElement) = element is PyiFile || PyUtil.turnDirIntoInit(element) is PyiFile

private fun isNamespacePackage(element: PsiElement): Boolean {
  if (element is PsiDirectory) {
    val level = PyUtil.getLanguageLevelForVirtualFile(element.project, element.virtualFile)
    if (!level.isPython2) {
      return PyUtil.turnDirIntoInit(element) == null
    }
  }
  return false
}

/**
 * See [https://www.python.org/dev/peps/pep-0561/#type-checker-module-resolution-order].
 */
private fun resolvedElementPriority(element: PsiElement, module: Module?) = when {
  isNamespacePackage(element) -> -2
  isUserFile(element, module) -> if (pyi(element)) 8 else 7
  isInStubPackage(element, module) -> 6
  isInTypeShed(element) -> 2
  isInInlinePackage(element, module) -> 4
  else -> 0
}

private fun isUserFile(element: PsiElement, module: Module?) =
  module != null &&
  element is PsiFileSystemItem &&
  element.virtualFile.let {
    it != null && ModuleUtilCore.moduleContainsFile(module, it, false)
  }

/**
 * See [resolveModuleAtStubPackage].
 */
private fun isInStubPackage(element: PsiElement, module: Module?) = element.getUserData(STUB_PACKAGE_KEY) == true && isPEP561Enabled(module)

private fun isInTypeShed(element: PsiElement) =
  pyi(element) && (element as? PsiFileSystemItem)?.virtualFile.let { it != null && PyTypeShed.isInside(it) }

/**
 * See [https://www.python.org/dev/peps/pep-0561/#packaging-type-information].
 * Value is cached in element's user data.
 */
private fun isInInlinePackage(element: PsiElement, module: Module?): Boolean {
  val cached = element.getUserData(INLINE_PACKAGE_KEY)
  if (cached != null) return cached

  val result = !pyi(element) &&
               (element is PyFile || PyUtil.turnDirIntoInit(element) is PyFile) &&
               isPEP561Enabled(module) &&
               isInInlinePackage((element as PsiFileSystemItem).virtualFile, element.project)

  element.putUserData(INLINE_PACKAGE_KEY, result)
  return result
}

/**
 * See [https://www.python.org/dev/peps/pep-0561/].
 * Value is cached in module's user data.
 */
private fun isPEP561Enabled(module: Module?): Boolean {
  if (module == null) return false

  val cached = module.getUserData(PEP_561_KEY)
  if (cached != null) return cached

  val result = PyUtil.getLanguageLevelForModule(module).isAtLeast(LanguageLevel.PYTHON37)

  module.putUserData(PEP_561_KEY, result)
  return result
}

/**
 * See [https://www.python.org/dev/peps/pep-0561/#packaging-type-information]
 */
private fun isInInlinePackage(file: VirtualFile?, project: Project): Boolean {
  if (file == null) return false

  val root = ProjectFileIndex.getInstance(project).getClassRootForFile(file) ?: return false
  var current = if (file.isDirectory) file else file.parent

  while (current != null && current != root && current.isDirectory) {
    val pyTyped = current.findChild("py.typed")
    if (pyTyped != null && !pyTyped.isDirectory) return true

    current = current.parent
  }

  return false
}