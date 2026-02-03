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
import com.jetbrains.python.codeInsight.typing.isRuntimeCheckable
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyClassPattern
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeMember
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
        node.arguments.getOrNull(1)?.let {
          checkRuntimeProtocol(it)
        }
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

    private fun checkRuntimeProtocol(base: PyExpression) {
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
      if (type is PyClassType && type.isProtocol(myTypeEvalContext) && !type.isRuntimeCheckable(myTypeEvalContext)) {
        registerProblem(base,
                        PyPsiBundle.message("INSP.protocol.only.runtime.checkable.protocols.can.be.used.with.instance.class.checks"),
                        GENERIC_ERROR)
      }
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
