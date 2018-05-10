// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.extensions.getQName
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.PyCustomMember
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyOverridingModuleMembersProvider
import com.jetbrains.python.psi.types.TypeEvalContext

class PyStdlibOverridingModuleMembersProvider: PyOverridingModuleMembersProvider() {

  override fun getMembersByQName(module: PyFile, qName: String, context: TypeEvalContext) = emptyList<PyCustomMember>()

  override fun resolveMember(module: PyFile, name: String, resolveContext: PyResolveContext): PsiElement? {
    if (module.languageLevel.isPython2 &&
        name == "md5" &&
        module.name == "hashlib.py" &&
        module.getQName() == QualifiedName.fromComponents("hashlib")) {
      // When module member is not found in it,
      // `__all__` and nearby module with the specified name are suggested as a fallback.

      // In case of `hashlib` in Python 2 stdlib, returned `md5` module is not used in `hashlib`
      // so the behaviour has to be overridden.

      // Please note that it's important to check against `hashlib.py`
      // because `hashlib.pyi` stub is defined correctly.

      return module.findExportedName(PyNames.ALL)
    }

    return null
  }
}