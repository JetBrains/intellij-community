// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.unresolvedReference

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ObjectUtils
import com.jetbrains.python.PyNames
import com.jetbrains.python.inspections.quickfix.AddFieldQuickFix
import com.jetbrains.python.inspections.quickfix.AddFunctionQuickFix
import com.jetbrains.python.inspections.quickfix.AddMethodQuickFix
import com.jetbrains.python.inspections.quickfix.CreateClassQuickFix
import com.jetbrains.python.inspections.quickfix.UnresolvedRefCreateFunctionQuickFix
import com.jetbrains.python.inspections.quickfix.UnresolvedRefTrueFalseQuickFix
import com.jetbrains.python.inspections.quickfix.UnresolvedReferenceAddParameterQuickFix
import com.jetbrains.python.inspections.quickfix.UnresolvedReferenceAddSelfQuickFix
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyImportStatement
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator
import com.jetbrains.python.psi.impl.references.PyOperatorReference
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.NonNls

/** `UnresolvedRefTrueFalseQuickFix` for `true` / `false` typos. */
internal fun getTrueFalseQuickFix(refText: String): LocalQuickFix? =
  if (refText == "true" || refText == "false") UnresolvedRefTrueFalseQuickFix(refText) else null

/** `UnresolvedRefCreateFunctionQuickFix` for unresolved unqualified calls like `foo()`. */
internal fun getCreateFunctionQuickFix(expr: PyReferenceExpression): LocalQuickFix? {
  val callExpression = PyCallExpressionNavigator.getPyCallExpressionByCallee(expr) ?: return null
  val callee = callExpression.callee
  val isUnqualifiedCallee = callee !is PyQualifiedExpression || callee.qualifier == null
  return if (isUnqualifiedCallee) UnresolvedRefCreateFunctionQuickFix(expr) else null
}

/** Offer adding a parameter to the enclosing function when an unknown name is used inside its body. */
internal fun getAddParameterQuickFix(refName: String?, expr: PyReferenceExpression?): LocalQuickFix? {
  PsiTreeUtil.getParentOfType(expr, PyFunction::class.java) ?: return null
  val isInsideDecoratorOrAnnotationOrImport =
    PsiTreeUtil.getParentOfType(expr, PyDecorator::class.java) != null ||
    PsiTreeUtil.getParentOfType(expr, PyAnnotation::class.java) != null ||
    PsiTreeUtil.getParentOfType(expr, PyImportStatement::class.java) != null
  if (isInsideDecoratorOrAnnotationOrImport) return null
  return UnresolvedReferenceAddParameterQuickFix(refName)
}

/**
 * Offer `self.<name>` / `cls.<name>` rewrites when the unresolved name actually exists as an
 * instance attribute, property, or method on the enclosing class. Walks instance attrs,
 * `@property` class-attrs, and methods to decide.
 */
internal fun getAddSelfFixes(typeEvalContext: TypeEvalContext, node: PyElement?, expr: PyReferenceExpression): List<LocalQuickFix> {
  val containedClass = PsiTreeUtil.getParentOfType(node, PyClass::class.java) ?: return emptyList()
  val function = PsiTreeUtil.getParentOfType(node, PyFunction::class.java) ?: return emptyList()
  val parameters = function.parameterList.parameters
  if (parameters.isEmpty()) return emptyList()
  val qualifier = parameters[0].text
  val isClassMethod = function.decoratorList?.decorators?.any { PyNames.CLASSMETHOD == it.callee?.text } ?: false

  val result: MutableList<LocalQuickFix> = ArrayList()
  for (target in containedClass.instanceAttributes) {
    if (!isClassMethod && node?.name == target.name) {
      result.add(UnresolvedReferenceAddSelfQuickFix(expr, qualifier))
    }
  }
  for (statement in containedClass.statementList.statements) {
    if (statement !is PyAssignmentStatement) continue
    val lhs = statement.leftHandSideExpression ?: continue
    if (lhs.text != expr.text) continue
    val assignedValue = statement.assignedValue as? PyCallExpression ?: continue
    val type = typeEvalContext.getType(assignedValue)
    if (type is PyClassTypeImpl && assignedValue.isCalleeText(PyNames.PROPERTY)) {
      result.add(UnresolvedReferenceAddSelfQuickFix(expr, qualifier))
    }
  }
  for (method in containedClass.methods) {
    if (expr.text == method.name) result.add(UnresolvedReferenceAddSelfQuickFix(expr, qualifier))
  }
  return result
}

/** `CreateClassQuickFix` when [refText] looks like a class name (CamelCase, not all-caps). */
internal fun getCreateClassFix(typeEvalContext: TypeEvalContext, @NonNls refText: String, element: PsiElement?): LocalQuickFix? {
  if (refText.length <= 2 || !refText[0].isUpperCase() || refText.uppercase() == refText) return null
  if (element !is PyQualifiedExpression) return null

  var qualifier = element.qualifier
  if (qualifier == null) {
    val fromImport = PsiTreeUtil.getParentOfType(element, PyFromImportStatement::class.java)
    if (fromImport != null) qualifier = fromImport.importSource
  }

  val destination: PsiFile = if (qualifier != null) {
    val type = typeEvalContext.getType(qualifier) as? PyModuleType ?: return null
    type.module
  }
  else {
    val injectionHost = InjectedLanguageManager.getInstance(element.project).getInjectionHost(element)
    ObjectUtils.chooseNotNull<PsiElement>(injectionHost, element).containingFile
  }
  return CreateClassQuickFix(refText, destination)
}

/**
 * Offer `AddField` / `AddMethod` / `AddFunction` / `CreateClass` fixes derived from the qualifier
 * type — what we add depends on whether the qualifier resolves to a user class, a module, or
 * something else.
 */
internal fun getCreateMemberFromUsageFixes(
  typeEvalContext: TypeEvalContext,
  type: PyType,
  reference: PsiReference,
  refText: String,
): List<LocalQuickFix> {
  val result: MutableList<LocalQuickFix> = ArrayList()
  val element = reference.element
  if (type is PyClassType) {
    val cls = type.pyClass
    if (!PyBuiltinCache.getInstance(element).isBuiltin(cls)) {
      if (element.parent is PyCallExpression) {
        result.add(AddMethodQuickFix(refText, cls.name, true))
      }
      else if (reference !is PyOperatorReference) {
        result.add(AddFieldQuickFix(refText, "None", type.name, true))
      }
    }
  }
  else if (type is PyModuleType) {
    val isQualifiedRefInsideImport = element is PyReferenceExpression && element.isQualified &&
                                     PsiTreeUtil.getParentOfType(element, PyImportStatementBase::class.java) != null
    if (!isQualifiedRefInsideImport) {
      val file = type.module
      val createClassQuickFix = getCreateClassFix(typeEvalContext, refText, element)
      if (createClassQuickFix != null) result.add(createClassQuickFix)
      else result.add(AddFunctionQuickFix(refText, file.name))
    }
  }
  return result
}
