/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.pyi

import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.DelegatingScopeProcessor
import com.intellij.psi.scope.PsiScopeProcessor
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyFileImpl
import com.jetbrains.python.psi.resolve.ImportedResolveResult
import com.jetbrains.python.psi.resolve.RatedResolveResult

/**
 * @author vlan
 */
class PyiFile(viewProvider: FileViewProvider) : PyFileImpl(viewProvider, PyiLanguageDialect.getInstance()) {
  override fun getFileType(): PythonFileType = PyiFileType.INSTANCE

  override fun toString(): String = "PyiFile:$name"

  override fun getLanguageLevel(): LanguageLevel = LanguageLevel.getLatest()

  override fun multiResolveName(name: String, exported: Boolean): List<RatedResolveResult> {
    if (name == "function" && PyBuiltinCache.getInstance(this).builtinsFile == this) return emptyList()
    if (exported && isPrivateName(name) && !resolvingBuiltinPathLike(name)) return emptyList()

    val baseResults = super.multiResolveName(name, exported)
    val dunderAll = dunderAll ?: emptyList()
    return if (exported)
      baseResults.filterNot { isPrivateImport((it as? ImportedResolveResult)?.definer, dunderAll) }
    else
      baseResults
  }

  override fun processDeclarations(processor: PsiScopeProcessor,
                                   resolveState: ResolveState,
                                   lastParent: PsiElement?,
                                   place: PsiElement): Boolean {
    val dunderAll = dunderAll ?: emptyList()
    val wrapper = object : DelegatingScopeProcessor(processor) {
      override fun execute(element: PsiElement, state: ResolveState): Boolean = when {
        isPrivateImport(element, dunderAll) -> true
        element is PsiNamedElement && isPrivateName(element.name) -> true
        else -> super.execute(element, state)
      }
    }
    return super.processDeclarations(wrapper, resolveState, lastParent, place)
  }

  private fun isPrivateName(name: String?) = PyUtil.getInitialUnderscores(name) == 1

  private fun isPrivateImport(element: PsiElement?, dunderAll: List<String>): Boolean {
    return element is PyImportElement && element.asName == null && element.visibleName !in dunderAll
  }

  private fun resolvingBuiltinPathLike(name: String): Boolean {
    return name == PyNames.BUILTIN_PATH_LIKE && PyBuiltinCache.getInstance(this).builtinsFile == this
  }
}
