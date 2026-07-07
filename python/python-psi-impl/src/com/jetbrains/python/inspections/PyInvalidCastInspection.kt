package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
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
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeUtil.derefOrUnknown
import com.jetbrains.python.psi.types.PyTypeUtil.isSubtypeRelated
import com.jetbrains.python.psi.types.PyTypedDictType
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyInvalidCastInspection : PyInspection() {
  @JvmField
  var ignoreGenericVariance: Boolean = true

  @JvmField
  var ignoreTypedDictStructure: Boolean = true

  override fun getOptionsPane(): OptPane {
    return OptPane.pane(
      OptPane.checkbox("ignoreGenericVariance", PyPsiBundle.message("INSP.invalid.cast.ignore.generic.variance")),
      OptPane.checkbox("ignoreTypedDictStructure", PyPsiBundle.message("INSP.invalid.cast.ignore.typed.dict.structure"))
    )
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val context = PyInspectionVisitor.getContext(session)
    val ignoreGenericVariance = ignoreGenericVariance
    val ignoreTypedDictStructure = ignoreTypedDictStructure
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
        val targetType = targetTypeRef.derefOrUnknown()
        val actualType = myTypeEvalContext.getType(args[1])

        if (targetType.isSubtypeRelated(actualType, myTypeEvalContext)) return

        // Treat a TypedDict as a plain 'dict' when the corresponding option is enabled, so that e.g. a
        // 'dict[str, object]' may be cast to a TypedDict and vice versa.
        if (ignoreTypedDictStructure &&
            targetType.eraseTypedDictStructure().isSubtypeRelated(actualType.eraseTypedDictStructure(), myTypeEvalContext)) {
          return
        }

        // The types become subtype-related once the variance of their generic arguments is ignored, so the mismatch
        // is purely about variance (e.g. casting the invariant 'list[int]' to 'list[object]'). Such casts are only
        // reported when the "ignore generic variance" option is disabled, and get a dedicated message.
        val relatedIgnoringVariance = isSubtypeRelatedIgnoringVariance(targetType, actualType, myTypeEvalContext)
        if (relatedIgnoringVariance && ignoreGenericVariance) return

        val fromName = PythonDocumentationProvider.getTypeName(actualType, myTypeEvalContext)
        val toName = PythonDocumentationProvider.getVerboseTypeName(targetType, myTypeEvalContext)

        val suggestedName = computeSuggestedIntermediateTypeName(targetType, actualType, myTypeEvalContext)

        val messageKey = if (relatedIgnoringVariance) "INSP.invalid.cast.variance.message" else "INSP.invalid.cast.message"

        registerProblem(
          callExpression,
          PyPsiBundle.problemMessage(
            messageKey,
            fromName,
            toName,
            suggestedName
          ),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
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
 * Checks whether two types are subtype-related while ignoring the variance of generic type arguments: two
 * parameterized types of related base classes are considered related as long as their corresponding type arguments
 * are themselves subtype-related, regardless of the direction required by the declared variance
 * (e.g. `list\[int]` and `list\[object]` are related, but `list\[str]` and `list\[int]` are not). This makes the check
 * insensitive to whether a generic parameter is invariant, covariant, or contravariant. Union members are
 * distributed over.
 */
private fun isSubtypeRelatedIgnoringVariance(t1: PyType?, t2: PyType?, context: TypeEvalContext): Boolean {
  if (t1 is PyUnionType) return t1.members.any { isSubtypeRelatedIgnoringVariance(it, t2, context) }
  if (t2 is PyUnionType) return t2.members.any { isSubtypeRelatedIgnoringVariance(t1, it, context) }

  if (t1 is PyClassType && t2 is PyClassType && t1.isParameterized && t2.isParameterized) {
    val base1 = PyClassTypeImpl(t1.pyClass, t1.isDefinition)
    val base2 = PyClassTypeImpl(t2.pyClass, t2.isDefinition)
    if (!base1.isSubtypeRelated(base2, context)) return false

    val args1 = t1.typeArguments
    val args2 = t2.typeArguments
    // Different arity means we can't align the arguments; the base-class relation is enough to consider them related.
    if (args1.size != args2.size) return true
    return args1.indices.all { isSubtypeRelatedIgnoringVariance(args1[it], args2[it], context) }
  }

  return t1.isSubtypeRelated(t2, context)
}

/**
 * Replaces TypedDict types with their underlying `dict` class, so that the subtype-relation check ignores the structural
 * details of a TypedDict and treats it as a plain dictionary (e.g. a `dict[str, object]` may be cast to a TypedDict).
 * Union members are erased element-wise.
 */
private fun PyType?.eraseTypedDictStructure(): PyType? = when (this) {
  is PyUnionType -> this.map { it.eraseTypedDictStructure() }
  is PyTypedDictType -> PyClassTypeImpl(this.pyClass, this.isDefinition)
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
