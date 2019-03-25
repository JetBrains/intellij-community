// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.PyCustomMember
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyClassMembersProviderBase
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyOverridingClassMembersProvider
import com.jetbrains.python.psi.types.TypeEvalContext

class PyOverridingClassDunderMembersProvider : PyClassMembersProviderBase(), PyOverridingClassMembersProvider {

  override fun getMembers(clazz: PyClassType, location: PsiElement?, context: TypeEvalContext): Collection<PyCustomMember> {
    if (PyUtil.isObjectClass(clazz.pyClass)) return emptyList()

    val pyLocation = location as? PyExpression
    val direction = if (pyLocation != null && context.maySwitchToAST(location)) AccessDirection.of(pyLocation) else AccessDirection.READ

    val result = mutableListOf<PyCustomMember>()

    if (clazz.isDefinition && clazz.pyClass.isNewStyleClass(context) || /* override */
        !clazz.isDefinition && !clazz.pyClass.isNewStyleClass(context) /* provide */) {
      result.addAll(resolveInObject(clazz, PyNames.__CLASS__, pyLocation, direction, context))
    }

    val (overridesDoc, overridesModule) = overridesDocOrModule(clazz, location)
    if (!overridesDoc) result.addAll(resolveInObject(clazz, PyNames.DOC, pyLocation, direction, context))
    if (!overridesModule) result.addAll(resolveInObject(clazz, "__module__", pyLocation, direction, context))

    return result
  }

  private fun resolveInObject(type: PyClassType,
                              name: String,
                              location: PyExpression?,
                              direction: AccessDirection,
                              context: TypeEvalContext): List<PyCustomMember> {
    val objectType = PyBuiltinCache.getInstance(type.pyClass).objectType ?: return emptyList()
    val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context)
    val results = objectType.resolveMember(name, location, direction, resolveContext) ?: return emptyList()
    return results.asSequence().mapNotNull { it.element }.map { PyCustomMember(name, it) }.toList()
  }

  private fun overridesDocOrModule(type: PyClassType, location: PsiElement?): Pair<Boolean, Boolean> {
    val cls = type.pyClass

    var overridesDoc = false
    var overridesModule = false

    val processor = object : PsiScopeProcessor {
      override fun execute(element: PsiElement, state: ResolveState): Boolean {
        if (element is PsiNamedElement) {
          when (element.name) {
            PyNames.DOC -> overridesDoc = true
            "__module__" -> overridesModule = true
          }
        }

        return !overridesDoc || !overridesModule
      }
    }

    cls.processClassLevelDeclarations(processor)
    if (!type.isDefinition) cls.processInstanceLevelDeclarations(processor, location)

    return overridesDoc to overridesModule
  }
}