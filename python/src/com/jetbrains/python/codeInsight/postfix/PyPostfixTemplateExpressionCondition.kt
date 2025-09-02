// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateExpressionCondition
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.extensions.inherits
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.*
import org.jdom.Element
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

interface PyPostfixTemplateExpressionCondition : PostfixTemplateExpressionCondition<PyExpression?> {
  override fun equals(other: Any?): Boolean
  override fun hashCode(): Int

  abstract class PySimpleConditionBase(@JvmField val id:String) : PyPostfixTemplateExpressionCondition {
    override fun getId(): String = id

    override fun equals(other: Any?): Boolean {
      return if (this === other) true else other != null && javaClass == other.javaClass
    }

    override fun hashCode(): Int {
      return javaClass.hashCode()
    }
  }

  class PyBooleanExpression : PySimpleConditionBase(ID) {
    override fun getPresentableName(): @Nls String = PyBundle.message("postfix.template.condition.boolean.name")

    override fun value(element: PyExpression): Boolean {
      val context = TypeEvalContext.codeCompletion(element.project, element.containingFile)
      val type = context.getType(element) ?: return false
      return PyBuiltinCache.getInstance(element).boolType == type
    }

    companion object {
      private const val ID: @NonNls String = "boolean"
    }
  }

  class PyNumberExpression : PySimpleConditionBase(ID) {
    override fun getPresentableName(): @Nls String = PyBundle.message("postfix.template.condition.number.name")

    override fun value(element: PyExpression): Boolean {
      val context = TypeEvalContext.codeCompletion(element.project, element.containingFile)
      val type = context.getType(element) ?: return false
      val builtinCache = PyBuiltinCache.getInstance(element)
      val intType = builtinCache.intType
      val floatType = builtinCache.floatType
      val complexType = builtinCache.complexType
      return type == intType || type == floatType || type == complexType
    }

    companion object {
      private const val ID: @NonNls String = "number"
    }
  }

  class PyStringExpression : PySimpleConditionBase(ID) {
    override fun getPresentableName(): @Nls String = PyBundle.message("postfix.template.condition.string.name")

    override fun value(element: PyExpression): Boolean {
      val context = TypeEvalContext.codeCompletion(element.project, element.containingFile)
      val type = context.getType(element) ?: return false
      val builtinCache = PyBuiltinCache.getInstance(element)
      val languageLevel = LanguageLevel.forElement(element)
      val types = setOfNotNull(builtinCache.getStringType(languageLevel), builtinCache.getByteStringType(languageLevel),
                               builtinCache.getUnicodeType(languageLevel))
      if (types.contains(type) || type is PyLiteralStringType) return true
      return types.filterIsInstance<PyUnionType>().any { it.members.contains(type) }
    }

    companion object {
      private const val ID: @NonNls String = "string"
    }
  }

  class PyIterable : PySimpleConditionBase(ID) {
    override fun getPresentableName(): @Nls String = PyBundle.message("postfix.template.condition.iterable.name")

    override fun value(element: PyExpression): Boolean {
      val context = TypeEvalContext.codeCompletion(element.project, element.containingFile)
      val type = context.getType(element) ?: return false
      return PyABCUtil.isSubtype(type, PyNames.ITERABLE, context)
    }

    companion object {
      private const val ID: @NonNls String = "iterable"
    }
  }

  abstract class PyCollectionTypeConditionBase(private val type: String, private val presentableNameKey: String) : PySimpleConditionBase(type) {
    override fun getPresentableName(): @Nls String = PyBundle.message(presentableNameKey)

    override fun value(element: PyExpression): Boolean {
      val context = TypeEvalContext.codeCompletion(element.project, element.containingFile)
      val elementType = context.getType(element) ?: return false
      if (elementType is PyCollectionType) {
        return elementType.classQName == type || elementType.pyClass.inherits(context, type)
      }
      if (elementType is PyClassLikeType) {
        return elementType.classQName == type || elementType.inherits(context, type)
      }
      return false
    }
  }

  class PyDict : PyCollectionTypeConditionBase("dict", "postfix.template.condition.dict.name")
  class PyList : PyCollectionTypeConditionBase("list", "postfix.template.condition.list.name")
  class PySet : PyCollectionTypeConditionBase("set", "postfix.template.condition.set.name")
  class PyTuple : PyCollectionTypeConditionBase("tuple", "postfix.template.condition.tuple.name")

  class PyNonNoneExpression : PySimpleConditionBase(ID) {
    override fun getPresentableName(): @Nls String = PyBundle.message("postfix.template.condition.non.none.name")

    override fun value(element: PyExpression): Boolean {
      val context = TypeEvalContext.codeCompletion(element.project, element.containingFile)
      val type = context.getType(element) ?: return false
      return !type.isNoneType
    }

    companion object {
      private const val ID: @NonNls String = "non none"
    }
  }

  class PyExceptionExpression : PySimpleConditionBase(ID) {
    override fun getPresentableName(): @Nls String = PyBundle.message("postfix.template.condition.exception.name")

    override fun value(element: PyExpression): Boolean {
      val context = TypeEvalContext.codeCompletion(element.project, element.containingFile)
      val type = context.getType(element) ?: return false
      if (type !is PyClassType) return false
      return PyUtil.isExceptionClass(type.pyClass)
    }

    companion object {
      private const val ID: @NonNls String = "exception"
    }
  }

  class PyBuiltinLenApplicable : PySimpleConditionBase(ID) {
    override fun getPresentableName(): @Nls String = PyBundle.message("postfix.template.condition.builtin.len.applicable.name")

    override fun value(element: PyExpression): Boolean {
      val context = TypeEvalContext.codeCompletion(element.project, element.containingFile)
      val type = context.getType(element) ?: return false
      return PyABCUtil.isSubtype(type, PyNames.SIZED, context)
    }

    companion object {
      private const val ID: @NonNls String = "builtin len applicable"
    }
  }

  data class PyClassCondition(private val name: QualifiedName) : PySimpleConditionBase(ID) {
    override fun getPresentableName(): @NlsSafe String = name.toString()

    override fun value(element: PyExpression): Boolean {
      var expression: PyExpression? = element
      if (!element.containingFile.isPhysical) {
        // Template engine creates a copy of the psi file and uses an element from the copy.
        // We need to use the original expression because type compatibility check relies
        // on `PsiElement.isEquivalentTo` which fails if a type of the expression is defined
        // in the same file as an expression (we get unequal (!=) objects after resolve: the desired
        // type is in the real file, and the expressionType is not).
        val at = CompletionUtil.getOriginalElement(element)
        expression = PsiTreeUtil.getParentOfType(at, PyExpression::class.java, false)
      }
      if (expression == null) return false
      val context = TypeEvalContext.codeCompletion(expression.project, expression.containingFile)
      val expressionType = context.getType(expression) ?: return false
      if (expressionType is PyClassLikeType) {
        val expected = name.toString()
        return expressionType.classQName == expected || expressionType.inherits(context, expected)
      }
      return false
    }

    override fun serializeTo(element: Element) {
      super.serializeTo(element)
      element.setAttribute(TYPE_ATTR, name.toString())
    }

    companion object {
      const val ID: @NonNls String = "type"
      const val TYPE_ATTR: @NonNls String = "type"
      fun readFrom(element: Element): PyClassCondition? {
        val value = element.getAttributeValue(TYPE_ATTR)
        return if (value != null) PyClassCondition(QualifiedName.fromDottedString(value)) else null
      }

      fun create(typeSpec: PyClass): PyClassCondition? {
        val name: String? = typeSpec.qualifiedName
        return if (name.isNullOrEmpty()) {
          null
        }
        else PyClassCondition(QualifiedName.fromDottedString(name))
      }

      fun create(typeName: String?): PyClassCondition? {
        return if (typeName.isNullOrEmpty()) {
          null
        }
        else PyClassCondition(QualifiedName.fromDottedString(typeName))
      }
    }
  }

  companion object {
    private fun getConditionsMap(vararg conditions: PyPostfixTemplateExpressionCondition): Map<String, PyPostfixTemplateExpressionCondition> {
      val result: MutableMap<String, PyPostfixTemplateExpressionCondition> = LinkedHashMap()
      for (condition in conditions) {
        result[condition.id] = condition
      }
      return result
    }

    // conditions we allow to select in postfix template editor UI
    @JvmField
    val PUBLIC_CONDITIONS = getConditionsMap(
      PyBooleanExpression(),
      PyNumberExpression(),
      PyStringExpression(),
      PyIterable(),
      PyDict(),
      PyList(),
      PySet(),
      PyTuple(),
      PyNonNoneExpression(),
      PyExceptionExpression(),
    )
  }
}

private fun PyWithAncestors.inherits(evalContext: TypeEvalContext, vararg parentNames: String): Boolean {
  val names = parentNames.toHashSet()
  return this.getAncestorTypes(evalContext).filterNotNull().mapNotNull(PyClassLikeType::getClassQName).any(names::contains)
}
