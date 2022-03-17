package com.jetbrains.python.psi.types

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.pyi.PyiUtil
import java.util.*


/**
 * Represents an arbitrary string literal
 * @see <a href="https://peps.python.org/pep-0675/">PEP 675 - Arbitrary Literal String Type
 */
class PyLiteralStringType private constructor(val cls: PyClass, val inferred: Boolean) : PyClassTypeImpl(cls, false) {

  override fun getName(): String {
    return "LiteralString"
  }

  override fun toString(): String {
    return if (inferred) "PyClassType: " + cls.qualifiedName else "PyLiteralStringType"
  }

  override fun isBuiltin(): Boolean {
    return inferred // if LiteralString is inferred then it's just str
  }

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other == null) return false

    val otherLiteralString = other as? PyLiteralStringType ?: return false
    return cls == otherLiteralString.cls && inferred == otherLiteralString.inferred
  }

  override fun hashCode(): Int {
    return Objects.hash(super.hashCode(), cls)
  }

  companion object {
    fun create(anchor: PsiElement, inferred: Boolean): PyClassType? {
      val strType = PyBuiltinCache.getInstance(anchor).strType
      if (Registry.`is`("python.type.hints.literal.string") && strType != null) {
        if (anchor is PyElement && PyiUtil.getOriginalLanguageLevel(anchor).isPy3K) {
          return PyLiteralStringType(strType.pyClass, inferred)
        }
      }
      return strType
    }

    fun match(expected: PyLiteralStringType, actual: PyClassType): Boolean {
      if (expected.inferred && expected.pyClass == actual.pyClass) return true
      return actual is PyLiteralStringType || actual is PyLiteralType && expected.pyClass == actual.pyClass
    }
  }
}
