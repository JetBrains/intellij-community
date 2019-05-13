/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.jetbrains.python.PyNames
import com.jetbrains.python.console.PydevConsoleRunner
import com.jetbrains.python.psi.PyUtil

data class PyQualifiedNameResolveContextImpl(private val psiManager: PsiManager, private val module: Module?,
                                             private val foothold: PsiElement?, private val sdk: Sdk?,
                                             private val relativeLevel : Int = -1,
                                             private val withoutRoots : Boolean = false,
                                             private val withoutForeign: Boolean = false,
                                             private val withMembers : Boolean = false,
                                             private val withPlainDirectories : Boolean = false,
                                             private val withoutStubs: Boolean = false) : PyQualifiedNameResolveContext {
  override fun getFoothold(): PsiElement? = foothold

  override fun getRelativeLevel(): Int = relativeLevel

  override fun getSdk(): Sdk? = sdk

  override fun getModule(): Module? = module

  override fun getProject(): Project = psiManager.project

  override fun getWithoutRoots(): Boolean = withoutRoots

  override fun getWithoutForeign(): Boolean = withoutForeign

  override fun getPsiManager(): PsiManager = psiManager

  override fun getWithMembers(): Boolean = withMembers

  override fun getWithPlainDirectories(): Boolean = withPlainDirectories

  override fun getWithoutStubs(): Boolean = withoutStubs

  override fun getEffectiveSdk(): Sdk? = if (visitAllModules) PydevConsoleRunner.getConsoleSdk(foothold) else sdk

  override fun isValid(): Boolean = footholdFile?.isValid ?: true

  override fun copyWithoutForeign(): PyQualifiedNameResolveContextImpl = copy(withoutForeign = true)

  override fun copyWithMembers(): PyQualifiedNameResolveContextImpl = copy(withMembers = true)

  override fun copyWithPlainDirectories(): PyQualifiedNameResolveContextImpl = copy(withPlainDirectories = true)

  override fun copyWithRelative(relativeLevel : Int): PyQualifiedNameResolveContextImpl = copy(relativeLevel = relativeLevel)

  override fun copyWithoutRoots(): PyQualifiedNameResolveContextImpl = copy(withoutRoots = true)

  override fun copyWithRoots(): PyQualifiedNameResolveContextImpl = copy(withoutRoots = false)

  override fun copyWithoutStubs(): PyQualifiedNameResolveContextImpl = copy(withoutStubs = true)

  override fun getContainingDirectory(): PsiDirectory? {
    val file = footholdFile ?: return null
    return if (relativeLevel > 0) ResolveImportUtil.stepBackFrom(file, relativeLevel) else file.containingDirectory
  }

  override fun getFootholdFile(): PsiFile? = when (foothold) {
    is PsiDirectory -> foothold.findFile(PyNames.INIT_DOT_PY)
    else -> foothold?.containingFile?.originalFile
  }

  override fun getVisitAllModules(): Boolean {
    val file = foothold
    return file != null && (PydevConsoleRunner.isInPydevConsole(file) || PyUtil.isInScratchFile(file))
  }
}