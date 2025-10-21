package com.jetbrains.python.psi.types

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.jetbrains.python.getEffectiveLanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.impl.PyBuiltinCache
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

  override fun <T : Any?> acceptTypeVisitor(visitor: PyTypeVisitor<T?>): T? {
    if (visitor is PyTypeVisitorExt) {
      return visitor.visitPyLiteralStringType(this)
    }
    return visitor.visitPyClassType(this)
  }

  companion object {
    fun create(anchor: PsiElement): PyClassType? {
      val strType = PyBuiltinCache.getInstance(anchor).strType
      if (Registry.`is`("python.type.hints.literal.string") && strType != null) {
        if (anchor is PyElement && getEffectiveLanguageLevel(anchor.getContainingFile()).isPy3K) {
          return PyLiteralStringType(strType.pyClass)
        }
      }
      return strType
    }

    fun match(expected: PyLiteralStringType, actual: PyClassType): Boolean {
      return actual is PyLiteralStringType || actual is PyLiteralType && expected.pyClass == actual.pyClass
    }
  }
}
