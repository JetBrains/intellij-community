// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.tensorFlow

import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.impl.PyImportResolver
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyTensorFlowImportResolver : PyImportResolver {

  override fun resolveImportReference(name: QualifiedName, context: PyQualifiedNameResolveContext, withRoots: Boolean): PsiElement? {
    return if (isTensorFlowMember(name)) {
      val resolveContext = context.copyWithoutForeign()
      resolveImportReference(name, resolveContext, getTensorFlowPathConfig(resolveContext.sdk))
    }
    else {
      null
    }
  }

  private fun isTensorFlowMember(name: QualifiedName) = name.firstComponent == "tensorflow" && name.componentCount > 1

  private fun resolveImportReference(name: QualifiedName,
                                     context: PyQualifiedNameResolveContext,
                                     pathConfig: Pair<Map<String, String>, String>): PsiElement? {
    pathConfig.first.forEach { (module, path) ->
      if (name.components[1] == module) {
        return takeFirstResolvedInTensorFlow("$path.${name.removeHead(2)}", context)
      }
    }

    return takeFirstResolvedInTensorFlow("${pathConfig.second}.${name.removeHead(1)}", context)
  }
}