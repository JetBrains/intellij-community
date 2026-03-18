// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyUtil

/**
 * Looks up [name] as an attribute, method, or nested class of [element].
 * Supports [PyFile] (module members), [PyClass] (methods/attributes), and [com.intellij.psi.PsiDirectory]
 * (converted to `__init__.py` via [PyUtil.turnDirIntoInit]).
 */
internal fun findMemberByName(element: PsiElement, name: String): PsiElement? {
  val target = PyUtil.turnDirIntoInit(element) ?: element
  return when (target) {
    is PyFile -> target.findTopLevelAttribute(name)
                 ?: target.findTopLevelFunction(name)
                 ?: target.findTopLevelClass(name)
    is PyClass -> target.findMethodByName(name, false, null)
                  ?: target.findInstanceAttribute(name, false)
                  ?: target.findClassAttribute(name, false, null)
    else -> null
  }
}

/**
 * Returns all members of [element] suitable for completion.
 * Supports [PyFile] (module members) and [PyClass] (methods/attributes).
 */
internal fun collectMemberVariants(element: PsiElement): List<PsiElement> {
  val target = PyUtil.turnDirIntoInit(element) ?: element
  return when (target) {
    is PyFile -> target.topLevelClasses + target.topLevelFunctions + target.topLevelAttributes.orEmpty()
    is PyClass -> target.getMethods().toList() + (target.classAttributes + target.instanceAttributes).filterNotNull()
    else -> emptyList()
  }
}
