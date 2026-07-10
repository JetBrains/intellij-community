// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ex.modifyAndCommitProjectProfile
import com.intellij.codeInspection.options.OptPane
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.elementType
import com.intellij.psi.util.lastLeaf
import com.intellij.psi.util.prevLeafs
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.inspections.quickfix.PyAddDunderMethodQuickFix
import com.jetbrains.python.inspections.PyInspectionMessages.CodifiedParam
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFormattedStringElement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringDunderUtil
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyFunctionType
import com.jetbrains.python.psi.types.PyIntersectionType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.PyUnsafeUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiUtil

/**
 * Warns when a type that doesn't define __str__, __repr__, or __format__
 * is converted to a string using str(), repr(), format(), f-strings, or print().
 */
class PyStringConversionWithoutDunderMethodInspection : PyInspection() {
  @JvmField
  val ignoredTypes: MutableList<String> = mutableListOf()

  @JvmField
  val reportedTypes: MutableList<String> = mutableListOf(
    "object", "types.FunctionType", "type"
  )

  override fun getOptionsPane(): OptPane {
    return OptPane.pane(
      OptPane.stringList("ignoredTypes", PyPsiBundle.message("INSP.string.conversion.ignored.types")),
      OptPane.stringList("reportedTypes", PyPsiBundle.message("INSP.string.conversion.reported.types"))
    )
  }

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session), this)
  }

  private class Visitor(
    holder: ProblemsHolder?,
    context: TypeEvalContext,
    private val inspection: PyStringConversionWithoutDunderMethodInspection,
  ) : PyInspectionVisitor(holder, context) {
    override fun visitPyCallExpression(node: PyCallExpression) {
      super.visitPyCallExpression(node)

      val callee = node.callee as? PyReferenceExpression ?: return

      // TODO: use `calleeType?.declarationElement?.qualifiedName` when overloads aren't union types PY-83781
      val resolvedCallee =
        PyResolveUtil.resolveDeclaration(callee.reference, PyResolveContext.defaultContext(myTypeEvalContext))
        ?: return

      val qualifiedCalleeName = PyNames.FQN.unqualifyBuiltinName((resolvedCallee as? PyQualifiedNameOwner)?.qualifiedName)

      when (qualifiedCalleeName) {
        "${PyNames.TYPE_STR}.__new__" -> node.arguments.firstOrNull()?.checkStringConversion(PyNames.DUNDER_STR)
        "repr" -> node.arguments.firstOrNull()?.checkStringConversion(PyNames.DUNDER_REPR)
        "format" -> node.arguments.firstOrNull()?.checkStringConversion(PyNames.DUNDER_FORMAT)
        "print" -> {
          // Check all arguments to print()
          for (argument in node.arguments) {
            if (argument is PyKeywordArgument) continue
            argument.checkStringConversion(PyNames.DUNDER_STR)
          }
        }
      }
    }

    override fun visitPyFormattedStringElement(node: PyFormattedStringElement) {
      super.visitPyFormattedStringElement(node)

      for (fragment in node.fragments) {

        val isDebug = fragment.formatPart == null &&
                      fragment.lastLeaf()
                        .prevLeafs
                        .firstOrNull {
                          it.elementType != PyTokenTypes.RBRACE &&
                          it.elementType != PyTokenTypes.WHITESPACE
                        }
                        ?.elementType == PyTokenTypes.EQ

        fragment.expression?.checkStringConversion(
          when (fragment.typeConversion?.text) {
            null if isDebug -> PyNames.DUNDER_REPR
            null -> PyNames.DUNDER_FORMAT
            "!s" -> PyNames.DUNDER_STR
            "!r", "!a" -> PyNames.DUNDER_REPR
            else -> continue
          }
        )
      }
    }

    private fun PyExpression.checkStringConversion(requiredMethod: String) =
      handleType(myTypeEvalContext.getType(this), requiredMethod)

    private fun PyExpression.handleType(type: PyType?, requiredMethod: String) {
      if (type == null) return

      when {
        type is PyUnionType -> {
          type.members.forEach { handleType(it, requiredMethod) }
        }
        type is PyIntersectionType || type is PyUnsafeUnionType -> {
          val members = type.members
          val anyMemberHasMethod = members.any { memberType ->
            when (memberType) {
              is PyClassType -> !memberType.shouldWarnForType(requiredMethod)
              else -> true
            }
          }
          if (!anyMemberHasMethod) {
            registerProblem(type, requiredMethod)
          }
        }
        type is PyFunctionType && "types.FunctionType" in inspection.reportedTypes -> {
          registerProblem(this, PyPsiBundle.problemMessage("INSP.string.not.helpful", "FunctionType"),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                          RemoveFromReportedTypesQuickFix("types.FunctionType", "FunctionType"))
        }
        type !is PyClassType -> return
        PyNames.FQN.unqualifyBuiltinName(type.classQName) in inspection.reportedTypes -> {
          val classQName = PyNames.FQN.unqualifyBuiltinName(type.classQName) ?: return
          val typeName = type.name ?: return
          registerProblem(this, PyPsiBundle.problemMessage("INSP.string.not.helpful", CodifiedParam.ofReference(type.pyClass, typeName)),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                          RemoveFromReportedTypesQuickFix(classQName, typeName))
        }
        type.isDefinition && "type" in inspection.reportedTypes -> {
          registerProblem(this, PyPsiBundle.problemMessage("INSP.string.not.helpful", "type"),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                          RemoveFromReportedTypesQuickFix("type", "type"))
        }
        type.shouldWarnForType(requiredMethod) -> registerProblem(type, requiredMethod)
      }
    }

    private fun PyExpression.registerProblem(type: PyType, requiredMethod: String) {
      val param = CodifiedParam.ofType(type, this, myTypeEvalContext)
      val message = when (requiredMethod) {
        PyNames.DUNDER_REPR -> PyPsiBundle.problemMessage("INSP.string.conversion.without.dunder.repr", param)
        PyNames.DUNDER_STR -> PyPsiBundle.problemMessage("INSP.string.conversion.without.dunder.str", param)
        PyNames.DUNDER_FORMAT -> PyPsiBundle.problemMessage("INSP.string.conversion.without.dunder.format", param)
        else -> return
      }
      if (type is PyClassType) {
        val classQName = PyNames.FQN.unqualifyBuiltinName(type.classQName) ?: return
        registerProblem(this, message,
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        AddToIgnoredTypesQuickFix(classQName, classQName.split(".").last()), PyAddDunderMethodQuickFix(type.pyClass, requiredMethod))
      }
      else registerProblem(this, message)
    }

    private fun PyClassLikeType?.shouldIgnoreType(requiredMethod: String): Boolean {
      val classQName = this?.classQName ?: return true
      if (classQName in inspection.ignoredTypes) return true
      // int and bool don't override __str__ in their stubs and have no runtime module to fall back to.
      return requiredMethod == PyNames.DUNDER_STR && classQName in PyStringDunderUtil.TYPES_WITH_BUILTIN_STR
    }

    private fun PyClassType.shouldWarnForType(requiredMethod: String): Boolean {
      if (PyNames.FQN.unqualifyBuiltinName(classQName) in inspection.ignoredTypes) return false

      // Check if any ancestor is in ignored types (e.g., class A(int) should be ignored
      // because int defines __str__ even though it's not in stubs)
      val pyClass = pyClass
      if (pyClass.getAncestorTypes(myTypeEvalContext).any { it.shouldIgnoreType(requiredMethod) }) {
        return false
      }

      val hasSyntheticRepr = parseDataclassParameters(pyClass, myTypeEvalContext)?.repr == true

      val hasRepr = hasSyntheticRepr || hasCustomStringMethod(PyNames.DUNDER_REPR)
      val hasStr by lazy { hasCustomStringMethod(PyNames.DUNDER_STR) }
      val hasFormat by lazy { hasCustomStringMethod(PyNames.DUNDER_FORMAT) }

      return when (requiredMethod) {
        PyNames.DUNDER_REPR -> !hasRepr
        PyNames.DUNDER_STR -> !hasRepr && !hasStr
        PyNames.DUNDER_FORMAT -> !hasRepr && !hasStr && !hasFormat
        else -> false
      }
    }

    private fun PyClassType.hasCustomStringMethod(
      methodName: String,
    ): Boolean {
      val objectMethodQName = "${PyNames.FQN.OBJECT}.$methodName"

      val memberInStub = findMember(methodName, PyResolveContext.defaultContext(myTypeEvalContext))
        .firstOrNull()
        ?.element
      if (memberInStub != null && (memberInStub as? PyFunction)?.qualifiedName != objectMethodQName) {
        return true
      }

      // Type stubs (.pyi) frequently omit __str__, __repr__, and __format__ even when the runtime
      // .py module defines them, so fall back to the implementation class to avoid false positives.
      val implementation = PyiUtil.getOriginalElementOrLeaveAsIs(pyClass, PyClass::class.java)
      val implementationMethod = implementation.findMethodInImplementations(methodName, myTypeEvalContext) ?: return false
      return implementationMethod.qualifiedName != objectMethodQName && implementationMethod.qualifiedName != "builtins.$objectMethodQName"
    }
  }
}

private class RemoveFromReportedTypesQuickFix(private val key: String, private val displayName: String) : LocalQuickFix {
  override fun getFamilyName(): String =
    PyPsiBundle.message("INSP.string.conversion.remove.from.reported.types", displayName)

  override fun startInWriteAction(): Boolean = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    modifyAndCommitProjectProfile(project) {
      val inspection = it.getUnwrappedTool(
        PyStringConversionWithoutDunderMethodInspection::class.java.simpleName,
        descriptor.psiElement
      ) as? PyStringConversionWithoutDunderMethodInspection ?: return@modifyAndCommitProjectProfile
      inspection.reportedTypes.remove(key)
    }
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
    IntentionPreviewInfo.EMPTY
}

private class AddToIgnoredTypesQuickFix(private val key: String, private val displayName: String) : LocalQuickFix {
  override fun getFamilyName(): String =
    PyPsiBundle.message("INSP.string.conversion.add.to.ignored.types", displayName)

  override fun startInWriteAction(): Boolean = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    modifyAndCommitProjectProfile(project) {
      val inspection = it.getUnwrappedTool(
        PyStringConversionWithoutDunderMethodInspection::class.java.simpleName,
        descriptor.psiElement
      ) as? PyStringConversionWithoutDunderMethodInspection ?: return@modifyAndCommitProjectProfile
      inspection.ignoredTypes.add(key)
    }
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
    IntentionPreviewInfo.EMPTY
}