// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNameIdentifierOwner
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.PyTypedDictType
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_FIELDS_PARAMETER
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_NAME_PARAMETER
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_TOTAL_PARAMETER

class PyTypedDictInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(holder, session)
  }

  private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
      val operandType = myTypeEvalContext.getType(node.operand)
      if (operandType !is PyTypedDictType) return

      val indexExpression = node.indexExpression
      val indexExpressionValueOptions = getIndexExpressionValueOptions(indexExpression)
      if (indexExpressionValueOptions.isNullOrEmpty()) {
        registerProblem(indexExpression, "TypedDict key must be a string literal; expected one of " +
                                         "(${operandType.fields.keys.joinToString(transform = { "'$it'" })})")
        return
      }

      val nonMatchingFields = indexExpressionValueOptions.filterNot { it in operandType.fields }
      if (nonMatchingFields.isNotEmpty()) {
        registerProblem(indexExpression, if (nonMatchingFields.size == 1)
          "TypedDict \"${operandType.name}\" has no key '${nonMatchingFields[0]}'"
        else "TypedDict \"${operandType.name}\" has no keys (${nonMatchingFields.joinToString(transform = { "'$it'" })})")
      }
    }

    override fun visitPyTargetExpression(node: PyTargetExpression) {
      val value = node.findAssignedValue()
      if (value is PyCallExpression && value.callee != null && PyTypedDictTypeProvider.isTypedDict(value.callee!!, myTypeEvalContext)) {
        val typedDictName = value.getArgument(0, TYPED_DICT_NAME_PARAMETER, PyExpression::class.java)
        if (typedDictName is PyStringLiteralExpression && node.name != typedDictName.stringValue) {
          registerProblem(typedDictName, "First argument has to match the variable name")
        }
      }
    }

    override fun visitPyArgumentList(node: PyArgumentList) {
      if (node.parent is PyClass && PyTypedDictTypeProvider.isTypingTypedDictInheritor(node.parent as PyClass, myTypeEvalContext)) {
        val arguments = node.arguments
        for (argument in arguments) {
          val type = myTypeEvalContext.getType(argument)
          if (argument !is PyKeywordArgument
              && type !is PyTypedDictType
              && !PyTypedDictTypeProvider.isTypedDict(argument, myTypeEvalContext)) {
            registerProblem(argument, "TypedDict cannot inherit from a non-TypedDict base class")
          }
          if (argument is PyKeywordArgument && argument.keyword == TYPED_DICT_TOTAL_PARAMETER && argument.valueExpression != null) {
            checkValidTotality(argument.valueExpression!!)
          }
        }
      }
      else if (node.callExpression != null) {
        val callExpression = node.callExpression
        val callee = callExpression!!.callee
        if (callee != null && PyTypedDictTypeProvider.isTypedDict(callee, myTypeEvalContext)) {
          val fields = callExpression.getArgument(1, TYPED_DICT_FIELDS_PARAMETER, PyExpression::class.java)
          if (fields !is PyDictLiteralExpression) {
            return
          }

          fields.elements.forEach {
            if (it !is PyKeyValueExpression) return

            checkValueIsAType(it.value, it.value?.text)
          }

          val totalityArgument = callExpression.getArgument(2, TYPED_DICT_TOTAL_PARAMETER, PyExpression::class.java)
          if (totalityArgument != null) {
            checkValidTotality(totalityArgument)
          }
        }
      }
    }

    override fun visitPyClass(node: PyClass) {
      if (!PyTypedDictTypeProvider.isTypingTypedDictInheritor(node, myTypeEvalContext)) return

      if (node.metaClassExpression != null) {
        registerProblem((node.metaClassExpression as PyExpression).parent, "Specifying a metaclass is not allowed in TypedDict")
      }

      val ancestorsFields = mutableMapOf<String, PyTypedDictType.FieldTypeAndTotality>()
      val typedDictAncestors = node.getAncestorTypes(myTypeEvalContext).filterIsInstance<PyTypedDictType>()
      typedDictAncestors.forEach { typedDict ->
        typedDict.fields.forEach { field ->
          val key = field.key
          val value = field.value
          if (key in ancestorsFields && !matchTypedDictFieldTypeAndTotality(ancestorsFields[key]!!, value)) {
            registerProblem(node.superClassExpressionList, "Cannot overwrite TypedDict field \'$key\' while merging")
          }
          else {
            ancestorsFields[key] = value
          }
        }
      }

      val singleStatement = node.statementList.statements.singleOrNull()
      if (singleStatement != null &&
          singleStatement is PyExpressionStatement &&
          singleStatement.expression is PyNoneLiteralExpression &&
          (singleStatement.expression as PyNoneLiteralExpression).isEllipsis) {
        registerProblem(tryGetNameIdentifier(singleStatement),
                        "Invalid statement in TypedDict definition; expected 'field_name: field_type'",
                        ProblemHighlightType.WEAK_WARNING)
        return
      }

      node.processClassLevelDeclarations { element, _ ->
        if (element !is PyTargetExpression) {
          registerProblem(tryGetNameIdentifier(element),
                          "Invalid statement in TypedDict definition; expected 'field_name: field_type'",
                          ProblemHighlightType.WEAK_WARNING)
          return@processClassLevelDeclarations true
        }
        if (element.hasAssignedValue()) {
          registerProblem(element.findAssignedValue(), "Right hand side values are not supported in TypedDict")
          return@processClassLevelDeclarations true
        }
        if (element.name in ancestorsFields) {
          registerProblem(element, "Cannot overwrite TypedDict field")
          return@processClassLevelDeclarations true
        }
        checkValueIsAType(element.annotation?.value, element.annotationValue)
        true
      }
    }

    override fun visitPyDelStatement(node: PyDelStatement) {
      for (target in node.targets) {
        for (expr in PyUtil.flattenedParensAndTuples(target)) {
          if (expr !is PySubscriptionExpression) continue
          val type = myTypeEvalContext.getType(expr.operand)
          if (type is PyTypedDictType) {
            val index = PyEvaluator.evaluate(expr.indexExpression, String::class.java)
            if (index == null || index !in type.fields) continue
            if (type.fields[index]!!.isRequired) {
              registerProblem(expr.indexExpression, "Key '$index' of TypedDict '${type.name}' cannot be deleted")
            }
          }
        }
      }
    }

    override fun visitPyCallExpression(node: PyCallExpression) {
      val callee = node.callee
      if (callee !is PyReferenceExpression || callee.qualifier == null) return

      val nodeType = myTypeEvalContext.getType(callee.qualifier!!)
      if (nodeType !is PyTypedDictType) return
      val arguments = node.arguments

      if (PyNames.UPDATE == callee.name) {
        inspectUpdateSequenceArgument(
          if (arguments.size == 1 && arguments[0] is PySequenceExpression) (arguments[0] as PySequenceExpression).elements else arguments,
          nodeType)
      }

      if (PyNames.CLEAR == callee.name || PyNames.POPITEM == callee.name) {
        if (nodeType.fields.any { it.value.isRequired }) {
          registerProblem(callee.nameElement?.psi, "This operation might break TypedDict consistency",
                          if (PyNames.CLEAR == callee.name) ProblemHighlightType.WARNING else ProblemHighlightType.WEAK_WARNING)
        }
      }

      if (PyNames.POP == callee.name) {
        val key = if (arguments.isNotEmpty()) PyEvaluator.evaluate(arguments[0], String::class.java) else null
        if (key != null && key in nodeType.fields && nodeType.fields[key]!!.isRequired) {
          registerProblem(callee.nameElement?.psi, "Key '$key' of TypedDict '${nodeType.name}' cannot be deleted")
        }
      }

      if (PyNames.SETDEFAULT == callee.name) {
        val key = if (arguments.isNotEmpty()) PyEvaluator.evaluate(arguments[0], String::class.java) else null
        if (key != null && key in nodeType.fields && !nodeType.fields[key]!!.isRequired) {
          if (node.arguments.size > 1) {
            val valueType = myTypeEvalContext.getType(arguments[1])
            if (!PyTypeChecker.match(nodeType.fields[key]!!.type, valueType, myTypeEvalContext)) {
              registerProblem(arguments[1], String.format("Expected type '%s', got '%s' instead",
                                                          PythonDocumentationProvider.getTypeName(nodeType.fields[key]!!.type,
                                                                                                  myTypeEvalContext),
                                                          PythonDocumentationProvider.getTypeName(valueType, myTypeEvalContext)))
            }
          }
        }
      }

      if (PyTypingTypeProvider.resolveToQualifiedNames(callee, myTypeEvalContext).contains(PyTypingTypeProvider.MAPPING_GET)) {
        val keyArgument = node.getArgument(0, "key", PyExpression::class.java) ?: return
        val key = PyEvaluator.evaluate(keyArgument, String::class.java)
        if (key == null) {
          registerProblem(keyArgument, "Key should be string")
          return
        }
        if (!nodeType.fields.containsKey(key)) {
          registerProblem(keyArgument, "TypedDict \"${nodeType.name}\" has no key '$key'\n")
        }
      }
    }

    override fun visitPyAssignmentStatement(node: PyAssignmentStatement) {
      val targetsToValuesMapping = node.targetsToValuesMapping
      node.targets.forEach { target ->
        if (target !is PySubscriptionExpression) return@forEach
        val targetType = myTypeEvalContext.getType(target.operand)
        if (targetType !is PyTypedDictType) return@forEach
        val indexString = PyEvaluator.evaluate(target.indexExpression, String::class.java)
        if (indexString == null) return@forEach

        val expected = targetType.getElementType(indexString)
        val actualExpressions = targetsToValuesMapping.filter { it.first == target }.map { it.second }
        actualExpressions.forEach { actual ->
          val actualType = myTypeEvalContext.getType(actual)
          if (!PyTypeChecker.match(expected, actualType, myTypeEvalContext)) {
            registerProblem(actual, String.format("Expected type '%s', got '%s' instead",
                                                  PythonDocumentationProvider.getTypeName(expected, myTypeEvalContext),
                                                  PythonDocumentationProvider.getTypeName(actualType, myTypeEvalContext)))
          }
        }
      }
    }

    private fun getIndexExpressionValueOptions(indexExpression: PyExpression?): List<String>? {
      if (indexExpression == null) return null
      val indexExprValue = PyEvaluator.evaluate(indexExpression, String::class.java)
      if (indexExprValue == null) {
        val type = myTypeEvalContext.getType(indexExpression) ?: return null
        val members = PyTypeUtil.toStream(type)
          .map { if (it is PyLiteralType) PyEvaluator.evaluate(it.expression, String::class.java) else null }
          .toList()
        return if (members.contains(null)) null
        else members
          .filterNotNull()
      }
      else {
        return listOf(indexExprValue)
      }
    }

    /**
     * Checks that [expression] with [strType] name is a type
     */
    private fun checkValueIsAType(expression: PyExpression?, strType: String?) {
      if (expression !is PyReferenceExpression && expression !is PySubscriptionExpression || strType == null) {
        registerProblem(expression, "Value must be a type", ProblemHighlightType.WEAK_WARNING)
        return
      }
      val type = Ref.deref(PyTypingTypeProvider.getStringBasedType(strType, expression, myTypeEvalContext))
      if (type == null && !PyTypingTypeProvider.resolveToQualifiedNames(expression, myTypeEvalContext).any { qualifiedName ->
          PyTypingTypeProvider.ANY == qualifiedName
        }) {
        registerProblem(expression, "Value must be a type", ProblemHighlightType.WEAK_WARNING)
      }
    }

    private fun tryGetNameIdentifier(element: PsiElement): PsiElement {
      return if (element is PsiNameIdentifierOwner) element.nameIdentifier ?: element else element
    }

    private fun checkValidTotality(totalityValue: PyExpression) {
      if (LanguageLevel.forElement(totalityValue.originalElement).isPy3K && totalityValue !is PyBoolLiteralExpression ||
          !listOf(PyNames.TRUE, PyNames.FALSE).contains(totalityValue.text)) {
        registerProblem(totalityValue, "Value of 'total' must be True or False")
      }
    }

    private fun matchTypedDictFieldTypeAndTotality(expected: PyTypedDictType.FieldTypeAndTotality,
                                                   actual: PyTypedDictType.FieldTypeAndTotality): Boolean {
      return expected.isRequired == actual.isRequired &&
             PyTypeChecker.match(expected.type, actual.type, myTypeEvalContext)
    }

    private fun inspectUpdateSequenceArgument(sequenceElements: Array<PyExpression>, typedDictType: PyTypedDictType) {
      sequenceElements.forEach {
        var key: PsiElement? = null
        var keyAsString: String? = null
        var value: PyExpression? = null

        if (it is PyKeyValueExpression && it.key is PyStringLiteralExpression) {
          key = it.key
          keyAsString = (it.key as PyStringLiteralExpression).stringValue
          value = it.value
        }
        else if (it is PyParenthesizedExpression) {
          val expression = PyPsiUtils.flattenParens(it)
          if (expression == null) return@forEach

          if (expression is PyTupleExpression && expression.elements.size == 2 && expression.elements[0] is PyStringLiteralExpression) {
            key = expression.elements[0]
            keyAsString = (expression.elements[0] as PyStringLiteralExpression).stringValue
            value = expression.elements[1]
          }
        }
        else if (it is PyKeywordArgument && it.valueExpression != null) {
          key = it.keywordNode?.psi
          keyAsString = it.keyword
          value = it.valueExpression
        }
        else return@forEach

        val fields = typedDictType.fields
        if (value == null) {
          return@forEach
        }
        if (keyAsString == null) {
          registerProblem(key, "Cannot add a non-string key to TypedDict ${typedDictType.name}")
          return@forEach
        }
        if (!fields.containsKey(keyAsString)) {
          registerProblem(key, "TypedDict ${typedDictType.name} cannot have key ${keyAsString}")
          return@forEach
        }
        val valueType = myTypeEvalContext.getType(value)
        if (!PyTypeChecker.match(fields[keyAsString]?.type, valueType, myTypeEvalContext)) {
          registerProblem(value, String.format("Expected type '%s', got '%s' instead",
                                               PythonDocumentationProvider.getTypeName(fields[keyAsString]!!.type, myTypeEvalContext),
                                               PythonDocumentationProvider.getTypeName(valueType, myTypeEvalContext)))
          return@forEach
        }
      }
    }
  }
}