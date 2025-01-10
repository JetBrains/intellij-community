// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider.Companion.TypedDictFieldQualifier
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_TOTAL_PARAMETER

class PyTypedDictInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
      val operandType = myTypeEvalContext.getType(node.operand)
      if (operandType !is PyTypedDictType) return

      val indexExpression = node.indexExpression
      val indexExpressionValueOptions = getIndexExpressionValueOptions(indexExpression)
      if (indexExpressionValueOptions.isNullOrEmpty()) {
        if (!operandType.isDefinition) {
          val keyList = operandType.fields.keys.joinToString(transform = { "'$it'" })
          registerProblem(indexExpression, PyPsiBundle.message("INSP.typeddict.typeddict.key.must.be.string.literal.expected.one", keyList))
        }
        return
      }

      val nonMatchingFields = indexExpressionValueOptions.filterNot { it in operandType.fields }
      if (nonMatchingFields.isNotEmpty()) {
        registerProblem(indexExpression, if (nonMatchingFields.size == 1)
          PyPsiBundle.message("INSP.typeddict.typeddict.has.no.key", operandType.name, nonMatchingFields[0])
        else {
          val nonMatchingFieldList = nonMatchingFields.joinToString(transform = { "'$it'" })
          PyPsiBundle.message("INSP.typeddict.typeddict.has.no.keys", operandType.name, nonMatchingFieldList)
        })
      }
    }

    override fun visitPyTargetExpression(node: PyTargetExpression) {
      val value = node.findAssignedValue()
      if (value is PyCallExpression && value.callee != null && PyTypedDictTypeProvider.isTypedDict(value.callee!!, myTypeEvalContext)) {
        val typedDictName = PyPsiUtils.flattenParens(value.arguments.firstOrNull())
        if (typedDictName is PyStringLiteralExpression && node.name != typedDictName.stringValue) {
          registerProblem(typedDictName, PyPsiBundle.message("INSP.typeddict.first.argument.has.to.match.variable.name"))
        }
      }
    }

    override fun visitPyArgumentList(node: PyArgumentList) {
      if (node.parent is PyClass && PyTypedDictTypeProvider.isTypingTypedDictInheritor(node.parent as PyClass, myTypeEvalContext)) {
        for (argument in node.arguments) {
          val type = myTypeEvalContext.getType(argument)
          if (!isValidSuperclass(argument, type)) {
            registerProblem(argument, PyPsiBundle.message("INSP.typeddict.typeddict.cannot.inherit.from.non.typeddict.base.class"))
          }
          if (argument is PyKeywordArgument) {
            val keyword = argument.keyword
            if (keyword == TYPED_DICT_TOTAL_PARAMETER) {
              val valueExpression = argument.valueExpression
              if (valueExpression != null) {
                checkValidTotality(valueExpression)
              }
            }
            else if (keyword != PyNames.METACLASS) {
              registerProblem(argument,
                              PyPsiBundle.message("INSP.typeddict.unexpected.argument.for.__init_subclass__.of.TypedDict", keyword))
            }
          }
        }
      }
      else {
        val callExpression = node.callExpression
        if (callExpression != null) {
          val callee = callExpression.callee
          if (callee != null && PyTypedDictTypeProvider.isTypedDict(callee, myTypeEvalContext)) {
            val argument1 = callExpression.arguments.getOrNull(1)
            val fields = PyPsiUtils.flattenParens(argument1)
            if (fields !is PyDictLiteralExpression) {
              registerProblem(argument1, PyPsiBundle.message("INSP.typeddict.expected.a.dictionary.literal"))
              return
            }

            fields.elements.forEach {
              if (it !is PyKeyValueExpression) return

              checkValueIsAType(it.value, it.value?.text)
            }

            val totalityArgument = callExpression.getKeywordArgument(TYPED_DICT_TOTAL_PARAMETER)
            if (totalityArgument != null) {
              checkValidTotality(totalityArgument)
            }
          }
        }
      }
    }

    private fun isValidSuperclass(argument: PyExpression, type: PyType?) =
      (argument is PyKeywordArgument ||
       type is PyTypedDictType ||
       PyTypedDictTypeProvider.isTypedDict(argument, myTypeEvalContext) ||
       argument is PySubscriptionExpression &&
       PyTypingTypeProvider.GENERIC == (myTypeEvalContext.getType(argument.operand) as? PyClassLikeType)?.classQName)

    override fun visitPyClass(node: PyClass) {
      if (!PyTypedDictTypeProvider.isTypingTypedDictInheritor(node, myTypeEvalContext)) return

      if (node.metaClassExpression != null) {
        registerProblem((node.metaClassExpression as PyExpression).parent,
                        PyPsiBundle.message("INSP.typeddict.specifying.metaclass.not.allowed.in.typeddict"))
      }

      val allAncestorsFields = mutableMapOf<String, MutableList<PyTypedDictType.FieldTypeAndTotality>>()
      val typedDictAncestors = node.getAncestorTypes(myTypeEvalContext).filterIsInstance<PyTypedDictType>()
      typedDictAncestors.forEach { typedDict ->
        typedDict.fields.forEach { field ->
          val key = field.key
          val value = field.value
          if (key !in allAncestorsFields) {
            allAncestorsFields[key] = mutableListOf()
          }
          val listOfFieldsForKey = allAncestorsFields[key]!!
          if (listOfFieldsForKey.isNotEmpty() && !matchTypedDictFieldTypeAndTotality(listOfFieldsForKey.first(), value)) {
            registerProblem(node.superClassExpressionList,
                            PyPsiBundle.message("INSP.typeddict.cannot.overwrite.typeddict.field.while.merging", key))
          }
          listOfFieldsForKey.add(value)
        }
      }

      val classTypedDictType = PyTypedDictTypeProvider.getTypedDictTypeForResolvedElement(node, myTypeEvalContext)
      node.processClassLevelDeclarations { element, _ ->
        if (element !is PyTargetExpression) {
          if (element is PyTypeParameter) {
            return@processClassLevelDeclarations true
          }
          registerProblem(element,
                          PyPsiBundle.message("INSP.typeddict.invalid.statement.in.typeddict.definition.expected.field.name.field.type"))
          return@processClassLevelDeclarations true
        }
        if (element.hasAssignedValue()) {
          registerProblem(element.findAssignedValue(),
                          PyPsiBundle.message("INSP.typeddict.right.hand.side.values.are.not.supported.in.typeddict"))
          return@processClassLevelDeclarations true
        }

        if (element.name in allAncestorsFields) {
          val fieldsForKey = allAncestorsFields[element.name]
          val classField = classTypedDictType?.fields[element.name]
          if (fieldsForKey != null && classField != null) {
            for (ancestorField in fieldsForKey) {
              if (!matchTypedDictFieldTypeAndTotality(ancestorField, classField)) {
                registerProblem(element, PyPsiBundle.message("INSP.typeddict.cannot.overwrite.typeddict.field"))
                return@processClassLevelDeclarations true
              }
            }
          }
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
            if (type.fields[index]!!.qualifiers.isRequired == true) {
              registerProblem(expr.indexExpression, PyPsiBundle.message("INSP.typeddict.key.cannot.be.deleted", index, type.name))
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
      var arguments = node.arguments

      if (PyNames.UPDATE == callee.name) {
        if (arguments.size == 1 && arguments[0] is PyReferenceExpression) {
          (PyUtil.resolveToTheTop(arguments[0]) as? PyTargetExpression)?.let { resolvedArg ->
            resolvedArg.findAssignedValue()?.let {
              arguments = arrayOf(it)
            }
          }
        }
        if (arguments.size == 1 && arguments[0] is PySequenceExpression) {
          arguments = (arguments[0] as PySequenceExpression).elements
        }
        inspectUpdateSequenceArgument(node, arguments, nodeType)
      }

      if (PyNames.CLEAR == callee.name || PyNames.POPITEM == callee.name) {
        registerProblem(callee.nameElement?.psi,
                        PyPsiBundle.message("INSP.typeddict.this.operation.might.break.typeddict.consistency"),
                        ProblemHighlightType.WARNING)
      }

      if (PyNames.POP == callee.name) {
        val key = if (arguments.isNotEmpty()) PyEvaluator.evaluate(arguments[0], String::class.java) else null
        if (key != null && key in nodeType.fields && nodeType.fields[key]!!.qualifiers.isRequired == true) {
          registerProblem(callee.nameElement?.psi, PyPsiBundle.message("INSP.typeddict.key.cannot.be.deleted", key, nodeType.name))
        }
      }

      if (PyNames.SETDEFAULT == callee.name) {
        val key = if (arguments.isNotEmpty()) PyEvaluator.evaluate(arguments[0], String::class.java) else null
        if (key != null && key in nodeType.fields && nodeType.fields[key]!!.qualifiers.isRequired == false) {
          if (node.arguments.size > 1) {
            val valueType = myTypeEvalContext.getType(arguments[1])
            if (!PyTypeChecker.match(nodeType.fields[key]!!.type, valueType, myTypeEvalContext)) {
              val expectedTypeName = PythonDocumentationProvider.getTypeName(nodeType.fields[key]!!.type,
                                                                             myTypeEvalContext)
              val actualTypeName = PythonDocumentationProvider.getTypeName(valueType, myTypeEvalContext)
              registerProblem(arguments[1],
                              PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", expectedTypeName, actualTypeName))
            }
          }
        }
      }

      if (PyTypedDictTypeProvider.isGetMethodToOverride(node, myTypeEvalContext)) {
        val keyArgument = node.getArgument(0, "key", PyExpression::class.java) ?: return
        val key = PyEvaluator.evaluate(keyArgument, String::class.java)
        if (key == null) {
          registerProblem(keyArgument, PyPsiBundle.message("INSP.typeddict.key.should.be.string"))
          return
        }
        if (!nodeType.fields.containsKey(key)) {
          registerProblem(keyArgument, PyPsiBundle.message("INSP.typeddict.typeddict.has.no.key", nodeType.name, key))
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
        if (targetType.fields[indexString]?.qualifiers?.isReadOnly == true) {
          registerProblem(target, PyPsiBundle.message("INSP.typeddict.typeddict.field.is.readonly", indexString))
        }

        val expected = targetType.getElementType(indexString)
        val actualExpressions = targetsToValuesMapping.filter { it.first == target }.map { it.second }
        actualExpressions.forEach { actual ->
          val actualType = myTypeEvalContext.getType(actual)
          if (!PyTypeChecker.match(expected, actualType, myTypeEvalContext)) {
            val expectedTypeName = PythonDocumentationProvider.getTypeName(expected, myTypeEvalContext)
            val actualTypeName = PythonDocumentationProvider.getTypeName(actualType, myTypeEvalContext)
            registerProblem(actual,
                            PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", expectedTypeName, actualTypeName))
          }
        }
      }
    }

    fun isTypeDictQualifier(node: PyReferenceExpression): Boolean =
      PyTypingTypeProvider.resolveToQualifiedNames(node, myTypeEvalContext).any { PyTypingTypeProvider.TYPE_DICT_QUALIFIERS.contains(it) }

    override fun visitPyReferenceExpression(node: PyReferenceExpression) {
      if (PsiTreeUtil.getParentOfType(node, PyImportStatementBase::class.java) == null) {
        val isTypeDictQualifier = isTypeDictQualifier(node)
        if (isTypeDictQualifier) {
          val qualifierName = node.name
          val classParent = PsiTreeUtil.getParentOfType(node, PyClass::class.java)
          val callParent = PsiTreeUtil.getParentOfType(node, PyCallExpression::class.java)
          if (classParent == null) {
            if (callParent == null) {
              registerProblem(node, PyPsiBundle.message("INSP.typeddict.qualifiers.cannot.be.used.outside.typeddict.definition",
                                                        qualifierName))
            }
            else {
              if (callParent.callee != null &&
                  PyTypingTypeProvider.resolveToQualifiedNames(callParent.callee!!, myTypeEvalContext).none { qualifiedName ->
                    PyTypingTypeProvider.TYPED_DICT == qualifiedName || PyTypingTypeProvider.TYPED_DICT_EXT == qualifiedName
                  }) {
                registerProblem(node, PyPsiBundle.message("INSP.typeddict.qualifiers.cannot.be.used.outside.typeddict.definition",
                                                          qualifierName))
              }
            }
          }
          else {
            if (!PyTypedDictTypeProvider.isTypingTypedDictInheritor(classParent, myTypeEvalContext)) {
              registerProblem(node, PyPsiBundle.message("INSP.typeddict.qualifiers.cannot.be.used.outside.typeddict.definition",
                                                        qualifierName))
            }
          }

          if (node.parent is PySubscriptionExpression && (node.parent as PySubscriptionExpression).indexExpression is PyTupleExpression) {
            registerProblem((node.parent as PySubscriptionExpression).indexExpression,
                            PyPsiBundle.message("INSP.typeddict.required.notrequired.must.have.exactly.one.type.argument",
                                                qualifierName))
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
          .map { if (it is PyLiteralType) PyEvaluator.evaluateNoResolve(it.expression, String::class.java) else null }
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
      if (expression !is PyExpression || strType == null) {
        registerProblem(expression, PyPsiBundle.message("INSP.typeddict.value.must.be.type"), ProblemHighlightType.WEAK_WARNING)
        return
      }
      if (expression is PySubscriptionExpression && expression.operand is PyReferenceExpression) {
        val qualifiers = PyTypedDictTypeProvider.getTypedDictFieldQualifiers(expression, myTypeEvalContext)
        if (qualifiers.count { it == TypedDictFieldQualifier.REQUIRED || it == TypedDictFieldQualifier.NOT_REQUIRED } > 1) {
          registerProblem(expression, PyPsiBundle.message("INSP.typeddict.required.and.not.required.cannot.be.nested"))
        }
        if (qualifiers.count { it == TypedDictFieldQualifier.READ_ONLY } > 1) {
          registerProblem(expression, PyPsiBundle.message("INSP.typeddict.read.only.cannot.be.nested"))
        }
        return
      }
      val type = if (expression is PyReferenceExpression) {
        Ref.deref(PyTypingTypeProvider.getType(expression, myTypeEvalContext))
      }
      else {
        Ref.deref(PyTypingTypeProvider.getStringBasedType(strType, expression, myTypeEvalContext))
      }
      if (type == null && !PyTypingTypeProvider.resolveToQualifiedNames(expression, myTypeEvalContext).any { qualifiedName ->
          PyTypingTypeProvider.ANY == qualifiedName
        }) {
        registerProblem(expression, PyPsiBundle.message("INSP.typeddict.value.must.be.type"), ProblemHighlightType.WEAK_WARNING)
      }
    }

    private fun checkValidTotality(totalityValue: PyExpression) {
      if (LanguageLevel.forElement(totalityValue.originalElement).isPy3K && totalityValue !is PyBoolLiteralExpression ||
          !listOf(PyNames.TRUE, PyNames.FALSE).contains(totalityValue.text)) {
        registerProblem(totalityValue, PyPsiBundle.message("INSP.typeddict.total.value.must.be.true.or.false"))
      }
    }

    private fun matchTypedDictFieldTypeAndTotality(expected: PyTypedDictType.FieldTypeAndTotality,
                                                   actual: PyTypedDictType.FieldTypeAndTotality): Boolean {
      return expected.qualifiers.isRequired == actual.qualifiers.isRequired &&
             PyTypeChecker.match(expected.type, actual.type, myTypeEvalContext)
    }

    private fun inspectUpdateSequenceArgument(updateCall: PyCallExpression, sequenceElements: Array<PyExpression>, typedDictType: PyTypedDictType) {
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
          registerProblem(key, PyPsiBundle.message("INSP.typeddict.cannot.add.non.string.key.to.typeddict", typedDictType.name))
          return@forEach
        }
        if (!fields.containsKey(keyAsString)) {
          registerProblem(key, PyPsiBundle.message("INSP.typeddict.typeddict.cannot.have.key", typedDictType.name, keyAsString))
          return@forEach
        }
        if (fields.get(keyAsString)!!.qualifiers.isReadOnly) {
          val warningHolder = (updateCall.callee as? PyReferenceExpression)?.nameElement?.psi ?: updateCall
          registerProblem(warningHolder, PyPsiBundle.message("INSP.typeddict.typeddict.field.is.readonly", keyAsString))
        }
        val valueType = myTypeEvalContext.getType(value)
        if (!PyTypeChecker.match(fields[keyAsString]?.type, valueType, myTypeEvalContext)) {
          val expectedTypeName = PythonDocumentationProvider.getTypeName(fields[keyAsString]!!.type, myTypeEvalContext)
          val actualTypeName = PythonDocumentationProvider.getTypeName(valueType, myTypeEvalContext)
          registerProblem(value, PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", expectedTypeName, actualTypeName))
          return@forEach
        }
      }
    }
  }
}