// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNameIdentifierOwner
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.inspectProtocolSubclass
import com.jetbrains.python.codeInsight.typing.isProtocol
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext

class PyProtocolInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(
    holder,PyInspectionVisitor.getContext(session))

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyClass(node: PyClass) {
      super.visitPyClass(node)

      val type = myTypeEvalContext.getType(node) as? PyClassType ?: return
      val superClassTypes = type.getSuperClassTypes(myTypeEvalContext)

      checkCompatibility(type, superClassTypes)
      checkProtocolBases(type, superClassTypes)
    }

    override fun visitPyCallExpression(node: PyCallExpression) {
      super.visitPyCallExpression(node)

      checkRuntimeProtocolInIsInstance(node)
      checkNewTypeWithProtocols(node)
    }

    private fun checkCompatibility(type: PyClassType, superClassTypes: List<PyClassLikeType?>) {
      superClassTypes
        .asSequence()
        .filterIsInstance<PyClassType>()
        .filter { isProtocol(it, myTypeEvalContext) }
        .forEach { protocol ->
          inspectProtocolSubclass(protocol, type, myTypeEvalContext).forEach {
            val subclassElements = it.second
            if (!subclassElements.isNullOrEmpty()) {
              checkMemberCompatibility(it.first, subclassElements, type, protocol)
            }
          }
        }
    }

    private fun checkProtocolBases(type: PyClassType, superClassTypes: List<PyClassLikeType?>) {
      if (!isProtocol(type, myTypeEvalContext)) return

      val correctBase: (PyClassLikeType?) -> Boolean = {
        if (it == null) true
        else {
          val classQName = it.classQName

          classQName == PyTypingTypeProvider.PROTOCOL ||
          classQName == PyTypingTypeProvider.PROTOCOL_EXT ||
          it is PyClassType && isProtocol(it, myTypeEvalContext)
        }
      }

      if (!superClassTypes.all(correctBase)) {
        registerProblem(type.pyClass.nameIdentifier, PyPsiBundle.message("INSP.protocol.all.bases.protocol.must.be.protocols"))
      }
    }

    private fun checkRuntimeProtocolInIsInstance(node: PyCallExpression) {
      if (node.isCalleeText(PyNames.ISINSTANCE, PyNames.ISSUBCLASS)) {
        val base = node.arguments.getOrNull(1) ?: return

        if (base is PyReferenceExpression) {
          val qNames = PyResolveUtil.resolveImportedElementQNameLocally(base).asSequence().map { it.toString() }
          if (qNames.any { it == PyTypingTypeProvider.PROTOCOL || it == PyTypingTypeProvider.PROTOCOL_EXT }) {
            registerProblem(base,
                            PyPsiBundle.message("INSP.protocol.only.runtime.checkable.protocols.can.be.used.with.instance.class.checks"),
                            GENERIC_ERROR)
            return
          }
        }

        val type = myTypeEvalContext.getType(base)
        if (
          type is PyClassType &&
          isProtocol(type, myTypeEvalContext) &&
          !PyKnownDecoratorUtil.getKnownDecorators(type.pyClass, myTypeEvalContext).any {
            it == TYPING_RUNTIME_CHECKABLE || it == TYPING_RUNTIME_CHECKABLE_EXT || it == TYPING_RUNTIME || it == TYPING_RUNTIME_EXT
          }
        ) {
          registerProblem(base,
                          PyPsiBundle.message("INSP.protocol.only.runtime.checkable.protocols.can.be.used.with.instance.class.checks"),
                          GENERIC_ERROR)
        }
      }
    }

    private fun checkNewTypeWithProtocols(node: PyCallExpression) {
      val resolveContext = PyResolveContext.defaultContext(myTypeEvalContext)

      node
        .multiResolveCalleeFunction(resolveContext)
        .firstOrNull { it.qualifiedName == PyTypingTypeProvider.NEW_TYPE }
        ?.let {
          val base = node.arguments.getOrNull(1)
          if (base != null) {
            val type = myTypeEvalContext.getType(base)
            if (type is PyClassLikeType && isProtocol(type, myTypeEvalContext)) {
              registerProblem(base, PyPsiBundle.message("INSP.protocol.newtype.cannot.be.used.with.protocol.classes"))
            }
          }
        }
    }

    private fun checkMemberCompatibility(protocolElement: PyTypedElement,
                                         subclassElements: List<RatedResolveResult>,
                                         type: PyClassType,
                                         protocol: PyClassType) {
      val expectedMemberType = myTypeEvalContext.getType(protocolElement)

      subclassElements
        .asSequence()
        .map { it.element }
        .filterIsInstance<PyTypedElement>()
        .filter { it.containingFile == type.pyClass.containingFile }
        .filterNot { PyTypeChecker.match(expectedMemberType, myTypeEvalContext.getType(it), myTypeEvalContext) }
        .forEach {
          val place = if (it is PsiNameIdentifierOwner) it.nameIdentifier else it
          registerProblem(place, PyPsiBundle.message("INSP.protocol.element.type.incompatible.with.protocol", it.name, protocol.name))
        }
    }
  }
}
