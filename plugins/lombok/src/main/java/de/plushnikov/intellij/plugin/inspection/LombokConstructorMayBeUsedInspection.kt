package de.plushnikov.intellij.plugin.inspection

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReceiverParameter
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiStatement
import com.intellij.psi.PsiThisExpression
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.siyeh.ig.psiutils.CommentTracker
import de.plushnikov.intellij.plugin.LombokBundle
import de.plushnikov.intellij.plugin.LombokClassNames
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AbstractConstructorClassProcessor
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo
import de.plushnikov.intellij.plugin.thirdparty.LombokCopyableAnnotations
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil

class LombokConstructorMayBeUsedInspection : LombokJavaInspectionBase() {
  override fun createVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : JavaElementVisitor() {
      override fun visitMethod(method: PsiMethod) {
        if (!method.isConstructor) return
        val annotationName = getAnnotationName(method) ?: return
        holder.registerProblem(
          method.nameIdentifier ?: return,
          LombokBundle.message("inspection.lombok.constructor.may.be.used.message", StringUtil.getShortName(annotationName)),
          ReplaceConstructorWithLombokFix(annotationName),
        )
      }
    }
  }

  private class ReplaceConstructorWithLombokFix(private val annotationName: String) : PsiUpdateModCommandQuickFix() {
    override fun getName() = LombokBundle.message("inspection.lombok.constructor.may.be.used.fix", StringUtil.getShortName(annotationName))

    override fun getFamilyName() = LombokBundle.message("inspection.lombok.constructor.may.be.used.fix.family")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
      val constructor = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false) ?: return
      if (getAnnotationName(constructor) != annotationName) return
      replaceConstructor(constructor, annotationName)
    }
  }
}

private fun replaceConstructor(constructor: PsiMethod, annotationName: String) {
  val modifiers = constructor.containingClass?.modifierList ?: return
  val annotation = modifiers.addAnnotation(annotationName)
  LombokProcessorUtil.convertModifierToLombokAccessLevel(constructor).ifPresent {
    val expression = PsiElementFactory.getInstance(constructor.project).createExpressionFromText(it, annotation)
    annotation.setDeclaredAttributeValue("access", expression)
  }
  CommentTracker().deleteAndRestoreComments(constructor)
  JavaCodeStyleManager.getInstance(constructor.project).shortenClassReferences(annotation)
}

private fun getAnnotationName(constructor: PsiMethod): String? {
  val psiClass = constructor.containingClass ?: return null
  if (psiClass.isInterface || psiClass.isAnnotationType || psiClass.isRecord || psiClass.modifierList == null) return null
  if (constructor.isVarArgs || constructor.hasTypeParameters() || constructor.throwsTypes.isNotEmpty() || constructor.annotations.isNotEmpty() || constructor.docComment != null || PsiTreeUtil.findChildOfType(
      constructor.parameterList,
      PsiReceiverParameter::class.java
    ) != null || PsiTreeUtil.hasErrorElements(constructor)
  ) return null

  val allFields = AbstractConstructorClassProcessor.getAllFields(psiClass)
  val requiredFields = AbstractConstructorClassProcessor.getRequiredFields(psiClass)
  if (allFields == requiredFields && PsiAnnotationSearchUtil.isAnnotatedWith(
      psiClass, LombokClassNames.ALL_ARGS_CONSTRUCTOR, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
  ) return null

  if (matches(constructor, requiredFields)
    && !PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR)
  ) {
    return LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
  }

  if (matches(constructor, allFields) && !PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, LombokClassNames.ALL_ARGS_CONSTRUCTOR)) {
    return LombokClassNames.ALL_ARGS_CONSTRUCTOR
  }
  return null
}

private fun matches(constructor: PsiMethod, fields: Collection<PsiField>): Boolean {
  val parameters = constructor.parameterList.parameters
  if (fields.isEmpty() || fields.size != parameters.size) return false

  val psiClass = constructor.containingClass ?: return false
  if (psiClass.fields.any { it !in fields && it.hasAnnotation(LombokClassNames.BUILDER_DEFAULT) }) return false
  val parameterNames = fields.map { AccessorsInfo.buildFor(it).removePrefixWithDefault(it.name) }
  if (parameterNames.distinct().size != parameterNames.size) return false

  val body = constructor.body ?: return false
  val statements = body.statements.toList().let {
    if (it.firstOrNull()?.let(::isEmptySuperCall) == true) it.drop(1) else it
  }
  if (statements.size != fields.size) return false

  val assignments = statements
    .asSequence()
    .mapNotNull { (it as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression }
    .filter { it.operationTokenType == JavaTokenType.EQ }
    .toList()
  if (assignments.size != fields.size) return false
  return fields.zip(parameters).all { (field, parameter) ->
    canReplaceParameter(field, parameter) && assignments.singleOrNull { doesAssignParameterToField(it, field, parameter) } != null
  }
}

fun doesAssignParameterToField(expr: PsiAssignmentExpression, field: PsiField, parameter: PsiParameter): Boolean {
  val left = PsiUtil.deparenthesizeExpression(expr.lExpression) as? PsiReferenceExpression ?: return false
  val right = PsiUtil.deparenthesizeExpression(expr.rExpression) as? PsiReferenceExpression ?: return false
  val lQualifier = PsiUtil.deparenthesizeExpression(left.qualifierExpression)
  return !(lQualifier != null && (lQualifier !is PsiThisExpression || lQualifier.qualifier != null)) && left.resolve() == field && right.qualifierExpression == null && right.resolve() == parameter
}

private fun canReplaceParameter(field: PsiField, parameter: PsiParameter): Boolean {
  if (field.type != parameter.type) return false
  if (PsiTreeUtil.findChildOfType(parameter.typeElement, PsiAnnotation::class.java) != null
    || PsiTreeUtil.findChildOfType(field.typeElement, PsiAnnotation::class.java) != null
  ) return false
  if (field.type !is PsiPrimitiveType && PsiAnnotationSearchUtil.isAnnotatedWith(field, *LombokUtils.NONNULL_ANNOTATIONS)) {
    if (!parameter.hasAnnotation(LombokClassNames.NON_NULL)) return false
    if (parameter.name != AccessorsInfo.buildFor(field).removePrefixWithDefault(field.name)) return false
  }
  val copyableAnnotations = LombokCopyableAnnotations.BASE_COPYABLE.collectCopyableAnnotations(field, field.containingClass).toMutableList()
  if (parameter.annotations.size != copyableAnnotations.size) return false
  for (annotation in parameter.annotations) {
    val index = copyableAnnotations.indexOfFirst { AnnotationUtil.equal(annotation, it) }
    if (index < 0) return false
    copyableAnnotations.removeAt(index)
  }
  return true
}

private fun isEmptySuperCall(statement: PsiStatement): Boolean {
  val call = (statement as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression ?: return false
  return call.methodExpression.referenceName == "super" && call.methodExpression.qualifierExpression == null && call.argumentList.isEmpty && call.typeArguments.isEmpty()
}
