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
import com.jetbrains.python.psi.impl.PyExpressionStatementImpl
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypedDictType

class PyTypedDictInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(holder, session)
  }

  private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
      super.visitPySubscriptionExpression(node)

      val rootOperand = node.rootOperand
      val rootOperandType = myTypeEvalContext.getType(rootOperand)
      if (rootOperandType !is PyTypedDictType) return

      val indexExpression = node.indexExpression
      val indexExprValue = getIndexExpressionAsString(indexExpression)
      if (indexExprValue == null) {
        registerProblem(indexExpression, "TypedDict key type must be string")
        return
      }

      if (!rootOperandType.fields.containsKey(indexExprValue)) {
        registerProblem(indexExpression, String.format("TypedDict '%s' cannot have key '%s'", rootOperandType.name, indexExprValue))
      }
    }

    override fun visitPyTargetExpression(node: PyTargetExpression?) {
      super.visitPyTargetExpression(node)
      if (node == null) return

      if (node.hasAssignedValue()) {
        val value = node.findAssignedValue()
        if (value is PyCallExpression && value.callee != null && PyTypedDictTypeProvider.isTypedDict(value.callee!!, myTypeEvalContext)) {
          if (value.arguments.isNotEmpty() && node.name != (value.arguments[0] as? PyStringLiteralExpression)?.stringValue) {
            registerProblem(value.arguments[0], "First argument has to match the variable name")
          }
        }
      }
    }

    override fun visitPyArgumentList(node: PyArgumentList) {
      super.visitPyArgumentList(node)

      if (node.parent is PyClass && PyTypedDictTypeProvider.isTypingTypedDictInheritor(node.parent as PyClass, myTypeEvalContext)) {
        val arguments = node.arguments
        for (argument in arguments) {
          val type = myTypeEvalContext.getType(argument)
          if (argument !is PyKeywordArgument
              && type !is PyTypedDictType
              && !PyTypedDictTypeProvider.isTypedDict(argument, myTypeEvalContext)) {
            registerProblem(argument, "TypedDict cannot inherit from a non-TypedDict base class")
          }
          if (argument is PyKeywordArgument && argument.keyword == "total" && !checkValidTotality(argument.valueExpression)) {
            registerProblem(argument.valueExpression, "Value of 'total' must be True or False")
          }
        }
      }
      else if (node.callExpression != null) {
        val callee = node.callExpression!!.callee
        if (callee != null && PyTypedDictTypeProvider.isTypedDict(callee, myTypeEvalContext)) {
          val totality = node.getKeywordArgument("total")?.valueExpression
          if (!checkValidTotality(totality)) {
            registerProblem(totality, "Value of 'total' must be True or False")
          }
          val fields = node.arguments.filterIsInstance<PySequenceExpression>().firstOrNull()
          if (fields == null) return

          fields.elements.forEach {
            if (it !is PyKeyValueExpression) return

            if (it.value !is PyReferenceExpression) {
              registerProblem(it.value, "Value must be a type")
            }
            val name = it.value?.name
            if (name != null) {
              val type = Ref.deref(PyTypingTypeProvider.getStringBasedType(name, it, myTypeEvalContext))
              if (type == null && !PyTypingTypeProvider.resolveToQualifiedNames(it.value!!, myTypeEvalContext).any { qualifiedName ->
                  PyTypingTypeProvider.ANY == qualifiedName
                }) {
                registerProblem(it.value, "Value must be a type")
              }
            }
          }
        }
      }
    }

    override fun visitPyClass(node: PyClass) {
      super.visitPyClass(node)

      if (LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON36) &&
          PyTypedDictTypeProvider.isTypingTypedDictInheritor(node, myTypeEvalContext)) {
        if (node.metaClassExpression != null) {
          registerProblem((node.metaClassExpression as PyExpression).parent, "Specifying a metaclass is not allowed in TypedDict")
        }

        val ancestorsFields = mutableMapOf<String, PyTypedDictType.FieldTypeAndTotality>()
        val typedDictAncestors = node.getAncestorTypes(myTypeEvalContext).filterIsInstance<PyTypedDictType>()
        typedDictAncestors.forEach { typedDict ->
          typedDict.fields.forEach { field ->
            val key = field.key
            val value = field.value
            if (ancestorsFields.containsKey(key) && ancestorsFields[key] != value) {
              registerProblem(node.superClassExpressionList,
                              "Cannot overwrite TypedDict field \'$key\' while merging")
            }
            else {
              ancestorsFields[key] = value
            }
          }
        }

        typedDictAncestors
          .flatMap { it.fields.entries }
          .map { it.key to it.value }
          .toMap(ancestorsFields)

        val statements = node.statementList.statements
        if (statements.size == 1) {
          val statement = statements[0]
          if (statement !is PyTypeDeclarationStatement
              && statement !is PyPassStatement
              && !isDocString(statement)) {
            registerProblem(tryGetNameIdentifier(statement), "Invalid statement in TypedDict definition; expected 'field_name: field_type'",
                            ProblemHighlightType.WEAK_WARNING)
            return
          }
        }

        node.processClassLevelDeclarations { element, _ ->
          if (element !is PyTargetExpression) {
            registerProblem(tryGetNameIdentifier(element), "Invalid statement in TypedDict definition; expected 'field_name: field_type'",
                            ProblemHighlightType.WEAK_WARNING)
            return@processClassLevelDeclarations true
          }
          if (element.hasAssignedValue()) {
            registerProblem(element.findAssignedValue(), "Right hand side values are not supported in TypedDict")
            return@processClassLevelDeclarations true
          }
          if (ancestorsFields.containsKey(element.name)) {
            registerProblem(element, "Cannot overwrite TypedDict field")
            return@processClassLevelDeclarations true
          }
          true
        }
      }
    }

    private fun tryGetNameIdentifier(element: PsiElement): PsiElement {
      return if (element is PsiNameIdentifierOwner) element.nameIdentifier ?: element else element
    }

    private fun isDocString(statement: PyStatement): Boolean {
      return statement is PyExpressionStatementImpl
             && (statement as PyExpressionStatement).expression is PyStringLiteralExpression
             && (statement.expression as PyStringLiteralExpression).isDocString
    }

    override fun visitPyDelStatement(node: PyDelStatement) {
      super.visitPyDelStatement(node)

      for (target in node.targets) {
        for (expr in PyUtil.flattenedParensAndTuples(target)) {
          if (expr !is PySubscriptionExpression) return
          val rootOp = expr.rootOperand
          val type = myTypeEvalContext.getType(rootOp)
          if (type is PyTypedDictType) {
            val index = getIndexExpressionAsString(expr.indexExpression)
            if (index == null || !type.fields.containsKey(index)) return
            if (type.fields[index]!!.isRequired) {
              registerProblem(expr.indexExpression, "Key '$index' of TypedDict '${type.name}' cannot be deleted")
            }
          }
        }
      }
    }

    override fun visitPyCallExpression(node: PyCallExpression) {
      super.visitPyCallExpression(node)

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
                          ProblemHighlightType.WEAK_WARNING)
        }
      }

      if (PyNames.POP == callee.name) {
        val key = if (arguments.isNotEmpty()) getIndexExpressionAsString(arguments[0]) else null
        if (key != null && nodeType.fields.containsKey(key) && nodeType.fields[key]!!.isRequired) {
          registerProblem(callee.nameElement?.psi, "Key '$key' of TypedDict '${nodeType.name}' cannot be deleted")
        }
      }

      if (PyNames.SETDEFAULT == callee.name) {
        val key = if (arguments.isNotEmpty()) getIndexExpressionAsString(arguments[0]) else null
        if (key != null && nodeType.fields.containsKey(key) && !nodeType.fields[key]!!.isRequired) {
          if (node.arguments.size > 1) {
            val valueType = myTypeEvalContext.getType(arguments[1])
            if (nodeType.fields[key]!!.type != valueType) {
              registerProblem(arguments[1], String.format("Expected type '%s', got '%s' instead",
                                                          PythonDocumentationProvider.getTypeName(nodeType.fields[key]!!.type,
                                                                                                  myTypeEvalContext),
                                                          PythonDocumentationProvider.getTypeName(valueType, myTypeEvalContext)))
            }
          }
        }
      }
    }

    private fun checkValidTotality(totalityExpression: PyExpression?): Boolean {
      val languageLevel = (totalityExpression?.containingFile as? PyFile)?.languageLevel ?: return false
      if (languageLevel.isAtLeast(LanguageLevel.PYTHON38)) return totalityExpression is PyBoolLiteralExpression
      else return listOf(PyNames.TRUE, PyNames.FALSE).contains(totalityExpression.text)
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
          var expression: PyExpression? = it
          while (expression is PyParenthesizedExpression) {
            expression = PyPsiUtils.flattenParens(expression)
          }
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

    private fun getIndexExpressionAsString(indexExpression: PyExpression?): String? {
      if (indexExpression is PyStringLiteralExpression) {
        return indexExpression.stringValue
      }

      var index = indexExpression
      var target = indexExpression?.reference?.resolve()
      while (index is PyReferenceExpression && target is PyTargetExpression) {
        index = target.findAssignedValue()
        target = index?.reference?.resolve()
      }
      if (index is PyStringLiteralExpression)
        return index.stringValue

      return null
    }
  }
}