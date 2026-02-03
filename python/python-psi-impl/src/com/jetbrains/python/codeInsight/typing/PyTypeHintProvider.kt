package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
@ApiStatus.Internal
interface PyTypeHintProvider {
  fun parseTypeHint(typeHint: PyExpression, alias: PyQualifiedNameOwner?, resolved: PsiElement, context: TypeEvalContext): Ref<PyType>?

  companion object {
    private val EP_NAME: ExtensionPointName<PyTypeHintProvider> = ExtensionPointName.create("Pythonid.typeHintProvider");

    fun parseTypeHint(typeHint: PyExpression, alias: PyQualifiedNameOwner?, resolved: PsiElement, context: TypeEvalContext): Ref<PyType>? {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.parseTypeHint(typeHint, alias, resolved, context) }
    }
  }
}
