package com.intellij.python.pyproject.model

import com.intellij.openapi.util.registry.RegistryValue
import kotlin.enums.EnumEntries

internal inline fun <reified T : Enum<T>> RegistryValue.asEnum(values: EnumEntries<T>): T {
  val selectedOption =
    selectedOption ?: throw AssertionError("$key bad value ${asString()}, did you forget to use '[possible|value*]' format?")
  return values.firstOrNull { it.name.equals(selectedOption, ignoreCase = true) } ?: throw AssertionError("$key's value $selectedOption can't be converted to ${T::class.java}")
}
