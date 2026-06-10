// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.unresolvedReference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.documentation.docstrings.DocStringParameterReference
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyImportStatement
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.impl.references.PyOperatorReference
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyFunctionType
import com.jetbrains.python.psi.types.PyImportedModuleType
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeUtil.toStream
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Return the canonical qualified names for a reference (even for an unresolved one).
 */
internal fun getCanonicalNames(reference: PsiReference, context: TypeEvalContext): List<QualifiedName> {
  val element = reference.element
  val result: MutableList<QualifiedName> = SmartList()
  when {
    reference is PyOperatorReference && element is PyQualifiedExpression -> {
      val receiver = reference.receiver ?: return result
      val type = context.getType(receiver) as? PyClassType ?: return result
      ContainerUtil.addIfNotNull(result, extractAttributeQNameFromClassType(element.referencedName, type))
    }
    element is PyReferenceExpression -> {
      val exprName = element.name ?: return result
      val qualifier = element.qualifier
      if (qualifier != null) {
        appendQualifiedNamesFromQualifierType(result, context.getType(qualifier), element, exprName)
      }
      else {
        appendQualifiedNamesForUnqualifiedReference(result, element, exprName)
      }
    }
    reference is DocStringParameterReference -> {
      ContainerUtil.addIfNotNull(result, QualifiedName.fromDottedString(reference.canonicalText))
    }
  }
  return result
}

private fun appendQualifiedNamesFromQualifierType(
  result: MutableList<QualifiedName>,
  qualifierType: PyType?,
  element: PyReferenceExpression,
  exprName: String,
) {
  qualifierType.toStream()
    .toList()
    .mapNotNullTo(result) { type -> qualifiedNameForType(type, element, exprName) }
}

private fun qualifiedNameForType(type: PyType?, element: PyReferenceExpression, exprName: String): QualifiedName? {
  return when (type) {
    is PyClassType -> extractAttributeQNameFromClassType(exprName, type)
    is PyModuleType -> {
      QualifiedNameFinder.findCanonicalImportPath(type.module, element)?.append(exprName)
    }
    is PyImportedModuleType -> {
      val resolved = type.importedModule.resolve() ?: return null
      QualifiedNameFinder.findCanonicalImportPath(resolved, element)?.append(exprName)
    }
    is PyFunctionType -> {
      val callable = type.callable
      val callableName = callable.name ?: return null
      QualifiedNameFinder.findCanonicalImportPath(callable, element)?.append(QualifiedName.fromComponents(callableName, exprName))
    }
    else -> null
  }
}

private fun appendQualifiedNamesForUnqualifiedReference(
  result: MutableList<QualifiedName>,
  element: PyReferenceExpression,
  exprName: String,
) {
  val parent = element.parent
  if (parent is PyImportElement) {
    when (val importStmt = PsiTreeUtil.getParentOfType(parent, PyImportStatementBase::class.java)) {
      is PyImportStatement -> {
        ContainerUtil.addIfNotNull(result, QualifiedName.fromComponents(exprName))
      }
      is PyFromImportStatement -> {
        val resolved: PsiElement = importStmt.resolveImportSource() ?: return
        val path = QualifiedNameFinder.findCanonicalImportPath(resolved, element) ?: return
        ContainerUtil.addIfNotNull(result, path.append(exprName))
      }
    }
  }
  else {
    val path = QualifiedNameFinder.findCanonicalImportPath(element, element) ?: return
    ContainerUtil.addIfNotNull(result, path.append(exprName))
  }
}

private fun extractAttributeQNameFromClassType(exprName: String?, type: PyClassType): QualifiedName? {
  val name = type.classQName ?: return null
  return QualifiedName.fromDottedString(name).append(exprName)
}
