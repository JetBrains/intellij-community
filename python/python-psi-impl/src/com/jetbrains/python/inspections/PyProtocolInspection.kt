// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNameIdentifierOwner
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.controlflow.PyTypeAssertionEvaluator.expandClassInfoExpressions
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.inspectProtocolSubclass
import com.jetbrains.python.codeInsight.typing.isProtocol
import com.jetbrains.python.codeInsight.typing.isRuntimeCheckable
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyClassPattern
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeMember
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyProtocolInspection : PyInspection() {

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = Visitor(
    holder, PyInspectionVisitor.getContext(session))

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

      if (node.isCalleeText(PyNames.ISINSTANCE, PyNames.ISSUBCLASS)) {
        val classInfoArg = node.arguments.getOrNull(1) ?: return
        checkRuntimeProtocol(classInfoArg)
        checkNonDataProtocolInIssubclass(node, classInfoArg)
        checkUnsafeOverlap(node, classInfoArg)
      }
      checkNewTypeWithProtocols(node)
      checkProtocolInstantiation(node)
    }

    override fun visitPyClassPattern(node: PyClassPattern) {
      checkRuntimeProtocol(node.classNameReference)
    }

    private fun checkCompatibility(type: PyClassType, superClassTypes: List<PyClassLikeType?>) {
      superClassTypes
        .asSequence()
        .filterIsInstance<PyClassType>()
        .filter { it.isProtocol(myTypeEvalContext) }
        .forEach { protocol ->
          inspectProtocolSubclass(protocol, type, myTypeEvalContext).forEach {
            val subclassElements = it.second
            if (subclassElements.isNotEmpty()) {
              checkMemberCompatibility(it.first, subclassElements, type, protocol)
            }
          }
        }
    }

    private fun checkProtocolBases(type: PyClassType, superClassTypes: List<PyClassLikeType?>) {
      if (!type.isProtocol(myTypeEvalContext)) return

      val correctBase: (PyClassLikeType?) -> Boolean = {
        if (it == null) true
        else {
          val classQName = it.classQName

          classQName == PyTypingTypeProvider.PROTOCOL ||
          classQName == PyTypingTypeProvider.PROTOCOL_EXT ||
          it is PyClassType && it.isProtocol(myTypeEvalContext)
        }
      }

      if (!superClassTypes.all(correctBase)) {
        registerProblem(type.pyClass.nameIdentifier, PyPsiBundle.message("INSP.protocol.all.bases.protocol.must.be.protocols"))
      }
    }

    /**
     * Ensures runtime checks are applied only to @runtime_checkable protocols.
     */
    private fun checkRuntimeProtocol(classInfoArg: PyExpression) {
      for (expression in expandClassInfoExpressions(classInfoArg)) {
        if (expression is PyReferenceExpression) {
          val qNames = PyResolveUtil.resolveImportedElementQNameLocally(expression).asSequence().map { it.toString() }
          if (qNames.any { it == PyTypingTypeProvider.PROTOCOL || it == PyTypingTypeProvider.PROTOCOL_EXT }) {
            registerProblem(expression,
                            PyPsiBundle.message("INSP.protocol.only.runtime.checkable.protocols.can.be.used.with.instance.class.checks"),
                            GENERIC_ERROR)
            continue
          }
        }

        val type = myTypeEvalContext.getType(expression)
        if (type is PyClassType && type.isProtocol(myTypeEvalContext) && !type.isRuntimeCheckable(myTypeEvalContext)) {
          registerProblem(expression,
                          PyPsiBundle.message("INSP.protocol.only.runtime.checkable.protocols.can.be.used.with.instance.class.checks"),
                          GENERIC_ERROR)
        }
      }
    }

    /**
     * `issubclass()` is valid only for non-data protocols.
     */
    private fun checkNonDataProtocolInIssubclass(call: PyCallExpression, classInfoArg: PyExpression) {
      if (!call.isCalleeText(PyNames.ISSUBCLASS)) return

      val protocolTypes = collectProtocolTypes(classInfoArg)
      for ((protocolType, expression) in protocolTypes) {
        if (isDataProtocol(protocolType)) {
          registerProblem(expression,
                          PyPsiBundle.message("INSP.protocol.only.non.data.protocols.can.be.used.with.issubclass"),
                          GENERIC_ERROR)
        }
      }
    }

    /**
     * Rejects runtime checks when the argument type has an unsafe structural overlap with a protocol.
     */
    private fun checkUnsafeOverlap(call: PyCallExpression, classInfoArg: PyExpression) {
      val protocolTypes = collectProtocolTypes(classInfoArg)
      if (protocolTypes.isEmpty()) return

      val objectArg = call.arguments.getOrNull(0) ?: return
      val objectType = myTypeEvalContext.getType(objectArg)
      if (objectType == null || PyTypeChecker.isUnknown(objectType, myTypeEvalContext)) return

      for ((protocolType, expression) in protocolTypes) {
        if (hasUnsafeOverlap(objectType, protocolType)) {
          registerProblem(expression,
                          PyPsiBundle.message("INSP.protocol.runtime.checkable.unsafe.overlap"),
                          GENERIC_ERROR)
        }
      }
    }

    /**
     * A type X unsafely overlaps with protocol P if:
     * 1. X is NOT assignable to P (structural type check fails)
     * 2. X IS assignable to the type-erased version of P (where all members have type Any)
     */
    private fun hasUnsafeOverlap(actualType: PyType?, protocol: PyClassType): Boolean {
      if (PyTypeChecker.isUnknown(actualType, myTypeEvalContext)) return false
      if (actualType is PyUnionType) {
        return actualType.members.any { hasUnsafeOverlap(it, protocol) }
      }

      val classType = actualType as? PyClassType ?: return false
      val instanceType = classType.toInstance()
      if (instanceType.isProtocol(myTypeEvalContext)) return false

      val members = inspectProtocolSubclass(protocol, instanceType, myTypeEvalContext)
      if (members.isEmpty()) return false
      if (members.any { it.second.isEmpty() }) return false

      return !PyTypeChecker.match(protocol.toInstance(), instanceType, myTypeEvalContext)
    }

    /**
     * Maps protocol class types to corresponding expression from the classinfo argument of `isinstance()` and `issubclass()` calls.
     */
    private fun collectProtocolTypes(classInfoArg: PyExpression): Map<PyClassType, PyExpression> {
      return expandClassInfoExpressions(classInfoArg)
        .mapNotNull { expression ->
          val type = myTypeEvalContext.getType(expression) as? PyClassType
          if (type != null && type.isProtocol(myTypeEvalContext)) { type to expression } else null
        }.toMap()
    }

    /**
     * Data protocol is a protocol that includes any non-method members (attributes or properties).
     */
    private fun isDataProtocol(protocol: PyClassType): Boolean {
      val members = inspectProtocolSubclass(protocol, protocol, myTypeEvalContext)
      for ((protocolMember, _) in members) {
        val element = protocolMember.element
        if (element is PyFunction) {
          if (element.property != null) return true
          continue
        }
        if (element != null) {
          return true
        }
      }
      return false
    }

    private fun checkNewTypeWithProtocols(node: PyCallExpression) {
      val callee = node.callee as? PyReferenceExpression ?: return
      val resolved = callee.followAssignmentsChain(resolveContext).element ?: return
      val isNewTypeCall = resolved is PyQualifiedNameOwner && resolved.qualifiedName == PyTypingTypeProvider.NEW_TYPE
      if (isNewTypeCall) {
        val base = node.arguments.getOrNull(1)
        if (base != null) {
          val type = myTypeEvalContext.getType(base)
          if (type is PyClassLikeType && type.isProtocol(myTypeEvalContext)) {
            registerProblem(base, PyPsiBundle.message("INSP.protocol.newtype.cannot.be.used.with.protocol.classes"))
          }
        }
      }
    }

    private fun checkMemberCompatibility(
      expectedMember: PyTypeMember,
      subclassMembers: List<PyTypeMember>,
      type: PyClassType,
      protocol: PyClassType,
    ) {
      subclassMembers
        .asSequence()
        .filter { it.element?.containingFile == type.pyClass.containingFile }
        .forEach {
          val element = it.element
          val place = if (element is PsiNameIdentifierOwner) element.nameIdentifier else element ?: return@forEach
          val elementName = if (element is PsiNameIdentifierOwner) element.name else return@forEach

          if (!PyTypeChecker.match(expectedMember.type, it.type, myTypeEvalContext)) {
            registerProblem(place, PyPsiBundle.message("INSP.protocol.element.type.incompatible.with.protocol", elementName, protocol.name))
          }
          else if (expectedMember.isWritable && !it.isWritable || expectedMember.isDeletable && !it.isDeletable) {
            registerProblem(place, PyPsiBundle.message("INSP.protocol.element.type.not.writable", elementName, protocol.name))
          }
        }
    }

    private fun checkProtocolInstantiation(node: PyCallExpression) {
      val calleeReferenceExpression = node.callee
      if (calleeReferenceExpression is PyReferenceExpression) {
        val resolveResult = calleeReferenceExpression.followAssignmentsChain(resolveContext)
        val cls = resolveResult.getElement()
        if (cls is PyClass) {
          if (cls.isProtocol(myTypeEvalContext)) {
            registerProblem(node, PyPsiBundle.message("INSP.protocol.cannot.instantiate.protocol.class", cls.name))
          }
        }
      }
    }
  }
}
