// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PyStubPackages")

package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.pyi.PyiUtil

const val TYPES_PREFIX = "types-"
const val STUBS_SUFFIX = "-stubs"
private val STUB_PACKAGE_KEY = Key<Boolean>("PY_STUB_PACKAGE")
private val INLINE_PACKAGE_KEY = Key<Boolean>("PY_INLINE_PACKAGE")

/**
 * If [name] argument points to element in stub package,
 * then [name] would be copied and `-stubs` suffix would be removed from the first component,
 * otherwise [name] would be returned.
 */
fun convertStubToRuntimePackageName(name: QualifiedName): QualifiedName {
  val top = name.firstComponent

  return when {
    top != null && top.endsWith(STUBS_SUFFIX) -> {
      top.removeSuffix(STUBS_SUFFIX)
    }
    top != null && top.startsWith(TYPES_PREFIX) -> {
      top.removePrefix(TYPES_PREFIX)
    }
    else -> return name
  }.let {
    QualifiedName.fromComponents(it).append(name.removeHead(1))
  }
}

/**
 * Returns stub package directory in the specified [dir] for the package with [referencedName] as a name.
 *
 * Requires [withoutStubs] to be False and [dir] to be lib root.
 */
fun findStubPackage(dir: PsiDirectory,
                    referencedName: String,
                    checkForPackage: Boolean,
                    withoutStubs: Boolean): PsiDirectory? {
  if (!withoutStubs && dir.virtualFile.let { it == getClassOrContentOrSourceRoot(dir.project, it) }) {
    val stubPackageName = "$referencedName$STUBS_SUFFIX"
    val stubPackage = dir.findSubdirectory(stubPackageName)

    // see comment about case sensitivity in com.jetbrains.python.psi.resolve.ResolveImportUtil.resolveInDirectory
    if (stubPackage?.name == stubPackageName && (!checkForPackage || PyUtil.isPackage(stubPackage, dir))) {
      doTransferStubPackageMarker(stubPackage)
      return stubPackage
    }

    val typesPackageName = "$TYPES_PREFIX$referencedName"
    val typesPackage = dir.findSubdirectory(typesPackageName)

    // see comment about case sensitivity in com.jetbrains.python.psi.resolve.ResolveImportUtil.resolveInDirectory
    if (typesPackage?.name == stubPackageName && (!checkForPackage || PyUtil.isPackage(typesPackage, dir))) {
      doTransferStubPackageMarker(typesPackage)
      return typesPackage
    }
  }

  return null
}

/**
 * Puts special mark to module resolved in stub package.
 */
fun transferStubPackageMarker(dir: PsiDirectory, resolvedSubmodule: PsiFile): PsiFile {
  if (dir.getUserData(STUB_PACKAGE_KEY) == true) resolvedSubmodule.putUserData(STUB_PACKAGE_KEY, true)
  return resolvedSubmodule
}

/**
 * Puts special mark to dir resolved in stub package.
 */
fun transferStubPackageMarker(dir: PsiDirectory, resolvedSubdir: PsiDirectory): PsiDirectory {
  if (dir.getUserData(STUB_PACKAGE_KEY) == true) doTransferStubPackageMarker(resolvedSubdir)
  return resolvedSubdir
}

private fun doTransferStubPackageMarker(resolvedSubdir: PsiDirectory) {
  resolvedSubdir.putUserData(STUB_PACKAGE_KEY, true)
  PyUtil.turnDirIntoInit(resolvedSubdir)?.putUserData(STUB_PACKAGE_KEY, true)
}

fun removeRuntimeModulesForWhomStubModulesFound(resolved: List<RatedResolveResult>): List<RatedResolveResult> {
  val stubPkgModules = mutableSetOf<String>()

  resolved.forEach {
    val stubPkgModule = it.element
    if (stubPkgModule is PyiFile && stubPkgModule.getUserData(STUB_PACKAGE_KEY) == true) stubPkgModules += stubPkgModule.name
  }

  return if (stubPkgModules.isEmpty()) resolved
  else resolved.filterNot {
    val runtimePkgModule = it.element
    runtimePkgModule is PyFile && runtimePkgModule !is PyiFile && stubPkgModules.contains(runtimePkgModule.name + "i") // py -> pyi
  }
}

private fun getClassOrContentOrSourceRoot(project: Project, file: VirtualFile): VirtualFile? {
  val index = ProjectFileIndex.getInstance(project)

  index.getClassRootForFile(file)?.let { return it }
  index.getSourceRootForFile(file)?.let { return it }
  index.getContentRootForFile(file)?.let { return it }

  return null
}

/**
 * See [findStubPackage] and [transferStubPackageMarker].
 */
fun isInStubPackage(element: PsiElement) = element.getUserData(STUB_PACKAGE_KEY) == true

/**
 * See [https://www.python.org/dev/peps/pep-0561/#packaging-type-information].
 * Value is cached in element's user data.
 */
internal fun isInInlinePackage(element: PsiElement, module: Module?): Boolean {
  if (module == null) return false

  val cached = element.getUserData(INLINE_PACKAGE_KEY)
  if (cached != null) return cached

  val result = !PyiUtil.isPyiFileOfPackage(element) && (element is PyFile || PyUtil.turnDirIntoInit(element) is PyFile) && getPyTyped(element) != null

  element.putUserData(INLINE_PACKAGE_KEY, result)
  return result
}

/**
 * See [https://www.python.org/dev/peps/pep-0561/#packaging-type-information]
 */
private fun getPyTyped(element: PsiElement?): VirtualFile? {
  if (element == null) return null
  val file = if (element is PsiFileSystemItem) element.virtualFile else element.containingFile?.virtualFile
  if (file == null) return null

  val root = getClassOrContentOrSourceRoot(element.project, file) ?: return null
  var current = if (file.isDirectory) file else file.parent

  while (current != null && current != root && current.isDirectory) {
    val pyTyped = current.findChild("py.typed")
    if (pyTyped != null && !pyTyped.isDirectory) return pyTyped

    current = current.parent
  }

  return null
}
