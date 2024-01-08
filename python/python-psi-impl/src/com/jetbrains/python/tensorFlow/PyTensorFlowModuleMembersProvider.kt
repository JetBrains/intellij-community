// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tensorFlow

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.PyCustomMember
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.*
import com.jetbrains.python.psi.types.PyModuleMembersProvider
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyTensorFlowModuleMembersProvider : PyModuleMembersProvider() {

  override fun getMembers(module: PyFile, point: PointInImport, context: TypeEvalContext): Collection<PyCustomMember> {
    return if (isTensorFlow(module)) {
      val resolveContext = fromFoothold(module).copyWithoutForeign()
      getMembers(resolveContext, getTensorFlowPathConfig(resolveContext.sdk))
    }
    else {
      emptyList()
    }
  }

  override fun resolveMember(module: PyFile, name: String, resolveContext: PyResolveContext): PsiElement? {
    return if (isTensorFlow(module)) {
      val context = fromFoothold(module).copyWithoutForeign()
      return resolveMember(name, context, getTensorFlowPathConfig(context.sdk))
    }
    else {
      null
    }
  }

  override fun getMembersByQName(module: PyFile, qName: String, context: TypeEvalContext): Collection<PyCustomMember> = emptyList()

  private fun isTensorFlow(module: PyFile): Boolean {
    return QualifiedNameFinder.findShortestImportableQName(module).let { it != null && it.matches("tensorflow") }
  }

  private fun getMembers(context: PyQualifiedNameResolveContext,
                         pathConfig: Map<String, String>): Collection<PyCustomMember> {
    val result = mutableListOf<PyCustomMember>()

    pathConfig.filterKeys { it != "*" }.mapNotNullTo(result) { (module, path) ->
      resolvedModuleToCustomMember(takeFirstResolvedInTensorFlow(path, context), module)
    }

    (takeFirstResolvedInTensorFlow(pathConfig["*"]!!, context) as? PsiDirectory)
      ?.let { dir ->
        dir.subdirectories.mapNotNullTo(result) { subdir ->
          resolvedModuleToCustomMember(subdir, subdir.name)
        }
      }

    return result
  }

  private fun resolveMember(name: String,
                            context: PyQualifiedNameResolveContext,
                            pathConfig: Map<String, String>): PsiElement? {
    if (name in pathConfig) {
      return takeFirstResolvedInTensorFlow(pathConfig.getValue(name), context)
    }

    (takeFirstResolvedInTensorFlow(pathConfig["*"]!!, context) as? PsiDirectory)
      ?.let { dir ->
        return dir.subdirectories.find { it.name == name }
      }

    return null
  }

  private fun resolvedModuleToCustomMember(module: PsiElement?, name: String): PyCustomMember? {
    return PyUtil.turnDirIntoInit(module)?.let { PyCustomMember(name, it) }
  }
}