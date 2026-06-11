package com.intellij.python.lsp.core.type

import com.intellij.openapi.util.Ref
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.typeRepresentation.psi.PyTypeRepresentationFile
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

object PyStringTypeResolver {
  fun resolvePyType(anchor: PyTypedElement, stringType: String): Ref<PyType?>? {
    if (stringType.isBlank())
      return null

    val codeInsightFallback = TypeEvalContext.externalContext(anchor.project)
    val qualifiedName = QualifiedName.fromDottedString(stringType)
    // Shortcut for builtins classes
    if (qualifiedName.matchesPrefix(QualifiedName.fromComponents("builtins"))) {
      val builtinClassName = qualifiedName.removeHead(1)
      if (builtinClassName.componentCount == 1) {
        val builtinClass = PyBuiltinCache.getInstance(anchor).getObjectType(builtinClassName.toString())
        if (builtinClass != null) {
          return Ref(builtinClass)
        }
      }
    }

    val file = FileContextUtil.getContextFile(anchor) ?: return null

    val typeExpression = PyTypeRepresentationFile(stringType, file).type ?: return null
    return PyTypingTypeProvider.getType(typeExpression, codeInsightFallback, true)
  }
}