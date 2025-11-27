package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.*

class PyInvalidCastInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, getContext(session)) {
      override fun visitPyCallExpression(callExpression: PyCallExpression) {
        val callees = callExpression.multiResolveCalleeFunction(resolveContext)
        val isCastCall = callees.any {
          (it as? PyFunction)?.qualifiedName == PyTypingTypeProvider.CAST ||
          (it as? PyFunction)?.qualifiedName == PyTypingTypeProvider.CAST_EXT
        }
        if (!isCastCall) return

        val args = callExpression.getArguments()
        if (args.size != 2) return
        val targetTypeRef: Ref<PyType>? = PyTypingTypeProvider.getType(args[0], myTypeEvalContext)
        val targetType = Ref.deref(targetTypeRef)
        val actualType = myTypeEvalContext.getType(args[1])

        if (PyTypeUtil.isOverlappingWith(targetType, actualType, myTypeEvalContext)) return
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

private fun computeSuggestedIntermediateTypeName(targetType: PyType?, actualType: PyType?, context: TypeEvalContext): String {
  val objectName = "object"

  fun toNonCollectionClassLike(t: PyType?): PyClassLikeType? = when (t) {
    is PyCollectionType -> null // avoid suggesting collection classes like 'list' as an intermediate type
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
