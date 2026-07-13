package com.jetbrains.python.psi.types

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.python.psi.PyClass
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyEnumMemberDeclarationProvider {
  fun allowsTupleMemberDeclaration(enumClass: PyClass, context: TypeEvalContext): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PyEnumMemberDeclarationProvider> =
      ExtensionPointName.create("Pythonid.pyEnumMemberDeclarationProvider")
  }
}
