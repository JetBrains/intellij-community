package com.jetbrains.python.psi.types

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Ref
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import org.jetbrains.annotations.ApiStatus

/**
 * Lets framework-specific enum implementations model how their metaclass/constructor transforms a member declaration
 * into the actual member value. For example, Django's `ChoicesType.__new__` treats a trailing `str`/`Promise` element
 * of a tuple declaration as the member's label and drops it, so `A = "x", "label"` has value type `str`, not
 * `tuple[str, str]`.
 */
@ApiStatus.Internal
interface PyEnumMemberDeclarationProvider {
  /**
   * Returns the member value type produced from [valueExpression] for [enumClass], wrapped in a [Ref] (which may hold
   * `null` for an unknown value type), or `null` if this provider does not transform declarations for [enumClass].
   */
  fun getMemberValueType(enumClass: PyClass, valueExpression: PyExpression, context: TypeEvalContext): Ref<PyType>?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PyEnumMemberDeclarationProvider> =
      ExtensionPointName.create("Pythonid.pyEnumMemberDeclarationProvider")
  }
}
