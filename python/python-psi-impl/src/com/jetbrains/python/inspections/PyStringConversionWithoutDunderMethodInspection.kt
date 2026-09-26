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
import com.jetbrains.python.pyi.PyiFile
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
    PyNames.FQN.OBJECT, PyNames.FQN.FUNCTION_TYPE, PyNames.FQN.TYPE
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

      // `str(x)` resolves to `builtins.str.__new__`, and so does an explicit `str.__new__(cls)` or a
      // `super().__new__(cls)` in a `str` subclass. Only the first one converts a value to a string.
      if (callee.name == PyNames.NEW) return

      // TODO: use `calleeType?.declarationElement?.qualifiedName` when overloads aren't union types PY-83781
      val resolvedCallee =
        PyResolveUtil.resolveDeclaration(callee.reference, PyResolveContext.defaultContext(myTypeEvalContext))
        ?: return

      val qualifiedCalleeName = PyNames.FQN.unqualifyBuiltinName((resolvedCallee as? PyQualifiedNameOwner)?.qualifiedName)

      when (qualifiedCalleeName) {
        "${PyNames.TYPE_STR}.${PyNames.NEW}" -> node.arguments.firstOrNull()?.checkStringConversion(PyNames.DUNDER_STR)
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
        type is PyFunctionType && PyNames.FQN.FUNCTION_TYPE in inspection.reportedTypes -> {
          registerProblem(this, PyPsiBundle.problemMessage("INSP.string.not.helpful", "FunctionType"),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                          RemoveFromReportedTypesQuickFix(PyNames.FQN.FUNCTION_TYPE, "FunctionType"))
        }
        type !is PyClassType -> return
        type.classQName in inspection.reportedTypes -> {
          val classQName = PyNames.FQN.unqualifyBuiltinName(type.classQName) ?: return
          val typeName = type.name ?: return
          registerProblem(this, PyPsiBundle.problemMessage("INSP.string.not.helpful", CodifiedParam.ofReference(type.pyClass, typeName)),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                          RemoveFromReportedTypesQuickFix(classQName, typeName))
        }
        type.isDefinition && PyNames.FQN.TYPE in inspection.reportedTypes -> {
          registerProblem(this, PyPsiBundle.problemMessage("INSP.string.not.helpful", "type"),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                          RemoveFromReportedTypesQuickFix(PyNames.FQN.TYPE, PyNames.TYPE))
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

    private fun PyClassType.shouldWarnForType(requiredMethod: String): Boolean {
      if (classQName in inspection.ignoredTypes) return false

      // Check if any ancestor is in ignored types (e.g., class A(B) is ignored when B is ignored)
      if (pyClass.getAncestorTypes(myTypeEvalContext).any { it?.classQName in inspection.ignoredTypes }) {
        return false
      }

      val hasSyntheticRepr = parseDataclassParameters(pyClass, myTypeEvalContext)?.repr == true

      val repr = if (hasSyntheticRepr) DunderMethodPresence.DEFINED else presenceOfDunderMethod(PyNames.DUNDER_REPR)
      val str by lazy { presenceOfDunderMethod(PyNames.DUNDER_STR) }
      val format by lazy { presenceOfDunderMethod(PyNames.DUNDER_FORMAT) }

      // Report only when every method that the conversion can use is absent. An unknown answer never reports.
      return when (requiredMethod) {
        PyNames.DUNDER_REPR -> repr.isAbsent
        PyNames.DUNDER_STR -> repr.isAbsent && str.isAbsent
        PyNames.DUNDER_FORMAT -> repr.isAbsent && str.isAbsent && format.isAbsent
        else -> false
      }
    }

    /**
     * The classes of the MRO, without `object`. A method of `object` is never a custom string conversion.
     * A `null` entry is an ancestor that does not resolve to a class.
     */
    private fun PyClassType.mroWithoutObject(): List<PyClass?> =
      (listOf<PyClassLikeType?>(this) + pyClass.getAncestorTypes(myTypeEvalContext))
        .filter { it?.classQName != PyNames.FQN.OBJECT }
        .map { (it as? PyClassType)?.pyClass }

    private fun PyClassType.presenceOfDunderMethod(methodName: String): DunderMethodPresence {
      // special case: no skeleton and no stubs
      if (classQName in PyNames.FQN.NONES) return DunderMethodPresence.DEFINED

      val objectMethodQName = "${PyNames.FQN.OBJECT}.$methodName"

      val memberInStub = findMember(methodName, PyResolveContext.defaultContext(myTypeEvalContext))
        .firstOrNull()
        ?.element
      if (memberInStub != null && (memberInStub as? PyFunction)?.qualifiedName != objectMethodQName) {
        return DunderMethodPresence.DEFINED
      }

      // A type stub keeps `__str__` and `__repr__` only when they change the signature of the `object` ones, so a
      // stub alone never proves that a class has no custom string conversion. Ask the runtime class of every class
      // of the MRO, and answer UNKNOWN when a stub has no runtime class and no known answer.
      var unknown = false
      var knownAbsent = false
      for (mroClass in mroWithoutObject()) {
        if (mroClass == null) {
          if (!knownAbsent) unknown = true
          continue
        }
        if (mroClass.findMethodByName(methodName, false, myTypeEvalContext) != null) return DunderMethodPresence.DEFINED

        // A known type answers for itself and for every ancestor of it. The MRO puts those ancestors after it, so a
        // stub among them adds nothing. A base of a subclass can also come later, and the walk must still see it.
        if (mroClass.qualifiedName in PyStringDunderUtil.TYPES_WITHOUT_USEFUL_STRING_CONVERSION) {
          knownAbsent = true
          continue
        }

        val implementation = PyiUtil.getOriginalElement(mroClass) as? PyClass
        if (implementation == null) {
          if (!knownAbsent && mroClass.containingFile is PyiFile) unknown = true
          continue
        }
        // A stub often hides a private base that the runtime module declares, so search the implementation ancestors.
        val implementationMethod = implementation.findMethodInImplementations(methodName, myTypeEvalContext)
        if (implementationMethod != null &&
            implementationMethod.qualifiedName != objectMethodQName &&
            implementationMethod.qualifiedName != "builtins.$objectMethodQName") {
          return DunderMethodPresence.DEFINED
        }
      }
      return if (unknown) DunderMethodPresence.UNKNOWN else DunderMethodPresence.NOT_DEFINED
    }
  }
}

/** What the IDE knows about a dunder method of a class. */
private enum class DunderMethodPresence {
  /** A class of the MRO, `object` apart, defines the method. */
  DEFINED,

  /** No class of the MRO defines the method, and every class of the MRO gives a reliable answer. */
  NOT_DEFINED,

  /** A class of the MRO comes from a type stub that has no runtime class, so the IDE cannot answer. */
  UNKNOWN;

  val isAbsent: Boolean get() = this == NOT_DEFINED
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