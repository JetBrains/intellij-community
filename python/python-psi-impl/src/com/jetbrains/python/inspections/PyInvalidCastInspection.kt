package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyCollectionType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeUtil.isOverlappingWith
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyInvalidCastInspection : PyInspection() {
  @JvmField
  var ignoreGenericVariance: Boolean = true

  override fun getOptionsPane(): OptPane {
    return OptPane.pane(
      OptPane.checkbox("ignoreGenericVariance", PyPsiBundle.message("INSP.invalid.cast.ignore.generic.variance"))
    )
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val context = PyInspectionVisitor.getContext(session)
    val ignoreGenericVariance = ignoreGenericVariance
    return object : PyInspectionVisitor(holder, context) {
      override fun visitPyCallExpression(callExpression: PyCallExpression) {
        val callees = callExpression.multiResolveCalleeFunction(resolveContext)
        val isCastCall = callees.any {
          (it as? PyFunction)?.qualifiedName == PyTypingTypeProvider.CAST ||
          (it as? PyFunction)?.qualifiedName == PyTypingTypeProvider.CAST_EXT
        }
        if (!isCastCall) return

        val args = callExpression.getArguments()
        if (args.size != 2) return
        val targetTypeRef = PyTypingTypeProvider.getType(args[0], myTypeEvalContext)
        val targetType = Ref.deref(targetTypeRef)
        val actualType = myTypeEvalContext.getType(args[1])

        if (targetType.isOverlappingWith(actualType, myTypeEvalContext)) return
        // When variance is ignored, generic type arguments are erased before the overlap check,
        // so e.g. casting 'list[int]' to 'list[object]' is treated as overlapping.
        if (ignoreGenericVariance &&
            targetType.eraseGenericParameters().isOverlappingWith(actualType.eraseGenericParameters(), myTypeEvalContext)) {
          return
        }
        val fromName = PythonDocumentationProvider.getTypeName(actualType, myTypeEvalContext)
        val toName = PythonDocumentationProvider.getVerboseTypeName(targetType, myTypeEvalContext)

        val suggestedName = computeSuggestedIntermediateTypeName(targetType, actualType, myTypeEvalContext)

        registerProblem(
          callExpression,
          PyPsiBundle.message(
            "INSP.invalid.cast.message",
            fromName,
            toName,
            suggestedName
          ),
          AddIntermediateCastQuickFix(suggestedName)
        )
      }
    }
  }
}

private class AddIntermediateCastQuickFix(private val typeText: String) : PsiUpdateModCommandQuickFix() {
  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.add.intermediate.cast", typeText)

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val call = element as? PyCallExpression ?: return
    val args = call.arguments
    if (args.size != 2) return
    val expr = args[1] ?: return

    val calleeText = call.callee?.text ?: "cast"
    val langLevel = LanguageLevel.forElement(call)
    val generator = PyElementGenerator.getInstance(project)
    val castExprText = "$calleeText($typeText, ${expr.text})"
    val newExpr = generator.createExpressionFromText(langLevel, castExprText)
    expr.replace(newExpr)
  }
}

/**
 * Erases generic type arguments so that parameterized types are compared by their base class only,
 * making the overlap check insensitive to the variance of generic parameters
 * (e.g. `list[int]` becomes plain `list`). Union members are erased element-wise.
 */
private fun PyType?.eraseGenericParameters(): PyType? = when (this) {
  is PyUnionType -> this.map { it.eraseGenericParameters() }
  is PyCollectionType -> PyClassTypeImpl(this.pyClass, this.isDefinition)
  else -> this
}

private fun computeSuggestedIntermediateTypeName(targetType: PyType?, actualType: PyType?, context: TypeEvalContext): String {
  val objectName = "object"

  fun toNonCollectionClassLike(t: PyType?): PyClassLikeType? = when (t) {
    is PyClassType if t.isParameterized -> null // avoid suggesting collection classes like 'list' as an intermediate type
    is PyClassLikeType -> t
    else -> null
  }

  val left = toNonCollectionClassLike(actualType)
  val right = toNonCollectionClassLike(targetType)
  if (left != null && right != null) {
    fun mro(t: PyClassLikeType): List<PyClassLikeType> {
      val result = ArrayList<PyClassLikeType>()
      result.add(t)
      result.addAll(t.getAncestorTypes(context).filterNotNull())
      return result
    }

    val leftMro = mro(left)
    val rightQNames = mro(right).mapNotNull { it.classQName }
    val rightSet = rightQNames.toSet()

    for (t in leftMro) {
      val qn = t.classQName
      if (qn != null && rightSet.contains(qn)) {
        val name = t.name
        if (name != null && name != objectName) {
          return name
        }
      }
    }
  }

  return objectName
}
