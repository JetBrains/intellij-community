package com.jetbrains.python.psi.types

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyElementTypes
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.pyi.PyiUtil
import java.util.*


/**
 * Represents an arbitrary string literal
 * @see <a href="https://peps.python.org/pep-0675/">PEP 675 - Arbitrary Literal String Type
 */
class PyLiteralStringType private constructor(val cls: PyClass) : PyClassTypeImpl(cls, false) {

  override fun getName(): String {
    return "LiteralString"
  }

  override fun toString(): String {
    return "PyLiteralStringType"
  }

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other == null) return false

    val otherLiteralString = other as? PyLiteralStringType ?: return false
    return cls == otherLiteralString.cls
  }

  override fun hashCode(): Int {
    return Objects.hash(super.hashCode(), cls)
  }

  companion object {
    fun create(anchor: PsiElement): PyClassType? {
      val strType = PyBuiltinCache.getInstance(anchor).strType
      if (Registry.`is`("python.type.hints.literal.string") && strType != null) {
        if (anchor is PyElement && PyiUtil.getOriginalLanguageLevel(anchor).isPy3K) {
          return PyLiteralStringType(strType.pyClass)
        }
      }
      return strType
    }

    fun match(expected: PyLiteralStringType, actual: PyClassType): Boolean {
      return actual is PyLiteralStringType || actual is PyLiteralType && expected.pyClass == actual.pyClass
    }

    fun fromLiteral(expression: PyExpression, context: TypeEvalContext): PyType? {
      val value = when (expression) {
        is PyKeywordArgument -> expression.valueExpression
        is PyParenthesizedExpression -> PyPsiUtils.flattenParens(expression)
        else -> expression
      }
      if (value is PyStringLiteralExpression) {
        val firstNode = value.stringNodes.firstOrNull()
        if (firstNode != null) {
          if (firstNode.elementType === PyElementTypes.FSTRING_NODE) {
            val allLiteralStringFragments: Boolean = value.stringElements.filterIsInstance<PyFormattedStringElement>().flatMap { it.fragments }.mapNotNull {
              if (it.expression != null) context.getType(it.expression!!) else null
            }.all { it is PyLiteralStringType }
            return if (allLiteralStringFragments) create(expression) else PyBuiltinCache.getInstance(expression).strType
          }
          return create(value)
        }
      }
      else if (value is PySequenceExpression) {
        val classes = if (value is PyDictLiteralExpression) {
          val keyTypes = value.elements.map { fromLiteral(it.key, context) }
          val valueTypes = value.elements.map { type -> type.value?.let { fromLiteral(it, context) } }
          listOf(PyUnionType.union(keyTypes), PyUnionType.union(valueTypes))
        }
        else value.elements.map { fromLiteral(it, context) }

        if (value is PyTupleExpression) {
          return PyTupleType.create(value, classes)
        }
        else {
          val name = when (value) {
            is PyListLiteralExpression -> "list"
            is PySetLiteralExpression -> "set"
            is PyDictLiteralExpression -> "dict"
            else -> null
          }
          return name?.let { PyCollectionTypeImpl.createTypeByQName(value, name, false, classes) }
        }
      }
      return context.getType(value ?: return null)
    }
  }
}
