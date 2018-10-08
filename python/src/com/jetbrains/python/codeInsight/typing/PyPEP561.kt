// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PyPEP561")

package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext
import com.jetbrains.python.psi.resolve.resolveModuleAt
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.sdk.PythonSdkType

private const val STUBS_SUFFIX = "-stubs"

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
        return resolveModuleAt(QualifiedName.fromComponents(nameInStubPackage.toList()), item, context).filter(::pyi)
      }
    }
  }

  return emptyList()
}

private fun contextLanguageLevel(context: PyQualifiedNameResolveContext): LanguageLevel {
  context.foothold?.also { return LanguageLevel.forElement(it) }
  context.footholdFile?.also { return LanguageLevel.forElement(it) }

  context.sdk?.also { return PythonSdkType.getLanguageLevelForSdk(it) }
  context.effectiveSdk?.also { return PythonSdkType.getLanguageLevelForSdk(it) }

  val moduleSdk = PythonSdkType.findPythonSdk(context.module) ?: return LanguageLevel.getDefault()
  return PythonSdkType.getLanguageLevelForSdk(moduleSdk)
}

private fun pyi(element: PsiElement) = element is PyiFile || PyUtil.turnDirIntoInit(element) is PyiFile