// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.tensorFlow

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.PyCustomMember
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.PointInImport
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.resolve.fromFoothold
import com.jetbrains.python.psi.types.PyModuleMembersProvider
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.NonExtendable
class PyTensorFlowModuleMembersProvider : PyModuleMembersProvider() {

  override fun getMembers(module: PyFile, point: PointInImport, context: TypeEvalContext): Collection<PyCustomMember> {
    if (point != PointInImport.AS_MODULE ||
        QualifiedNameFinder.findShortestImportableQName(module).let { it == null || !it.matches("tensorflow") }) return emptyList()

    // provide members for `from tensorflow.<caret>` reference
    // provided modules and subpackages are appended in runtime and have original location in other places

    val resolveContext = fromFoothold(module).copyWithoutForeign()
    val result = mutableListOf<PyCustomMember>()

    takeFirstResolvedInTensorFlow(KERAS, resolveContext)
      ?.let { PyUtil.turnDirIntoInit(it) }
      ?.let { result.add(PyCustomMember("keras", it)) }

    takeFirstResolvedInTensorFlow(ESTIMATOR, resolveContext)
      ?.let { PyUtil.turnDirIntoInit(it) }
      ?.let { result.add(PyCustomMember("estimator", it)) }

    resolveInTensorFlow(OTHERS, resolveContext)
      .mapNotNull { it.filterIsInstance<PsiDirectory>().firstOrNull() }
      .firstOrNull()
      ?.let { dir ->
        dir.subdirectories.forEach { subdir ->
          PyUtil.turnDirIntoInit(subdir)?.let { result.add(PyCustomMember(subdir.name, it)) }
        }
      }

    return result
  }

  override fun resolveMember(module: PyFile, name: String, resolveContext: PyResolveContext): PsiElement? = null

  override fun getMembersByQName(module: PyFile, qName: String, context: TypeEvalContext): Collection<PyCustomMember> = emptyList()
}