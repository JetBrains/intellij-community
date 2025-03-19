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
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyFileImpl
import com.jetbrains.python.psi.resolve.ImportedResolveResult
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.TypeEvalContext

class PyiFile(viewProvider: FileViewProvider) : PyFileImpl(viewProvider, PyiLanguageDialect.getInstance()) {
  override fun getFileType(): PythonFileType = PyiFileType.INSTANCE

  override fun toString(): String = "PyiFile:$name"

  override fun getLanguageLevel(): LanguageLevel = LanguageLevel.getLatest()

  override fun multiResolveName(name: String, exported: Boolean): List<RatedResolveResult> {
    if (name == "function" && PyBuiltinCache.getInstance(this).builtinsFile == this) return emptyList()

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
    val completingInPyiStub = PyiUtil.isInsideStub(place)
    val wrapper = object : DelegatingScopeProcessor(processor) {
      override fun execute(element: PsiElement, state: ResolveState): Boolean = when {
        // According to the typing spec, underscored names in .pyi stubs should be considered
        // "stub-only" objects (https://typing.readthedocs.io/en/latest/guides/writing_stubs.html#stub-only-objects).
        // However, they can still be re-used between .pyi stubs and imported in .py files under typing.TYPE_CHECKING.
        // Also, they can point to some inadvertently exposed de-facto API of some .py module.
        // Therefore, we do allow resolving such names, but don't suggest them among completion variants
        // to avoid polluting the lookup with internal names like "_T", "_Ts", etc.
        !completingInPyiStub && element is PsiNamedElement && isPrivateName(element.name) -> true
        isPrivateImport(element, dunderAll) -> true
        else -> super.execute(element, state)
      }
    }
    return super.processDeclarations(wrapper, resolveState, lastParent, place)
  }

  private fun isPrivateName(name: String?) = PyUtil.getInitialUnderscores(name) == 1

  private fun isPrivateImport(element: PsiElement?, dunderAll: List<String>): Boolean {
    // See https://typing.readthedocs.io/en/latest/spec/distributing.html#import-conventions 
    // and https://peps.python.org/pep-0484/#stub-files
    return element is PyImportElement && element.asName != element.importedQName?.toString() && element.visibleName !in dunderAll
  }

  override fun prioritizeNameRedefinitions(definitions: MutableList<PsiNamedElement>, typeEvalContext: TypeEvalContext) {
    // .pyi stubs contain only declarations. Thus, names cannot be redefined, and the original order is important.
  }
}
