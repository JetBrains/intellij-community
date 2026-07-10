package com.jetbrains.python.psi.stubs

/**
 * Pydantic-specific field attributes.
 *
 * For non-Pydantic fields these values are always `null`/empty. They are only set for fields
 * on classes recognized as Pydantic models.
 */
interface PydanticFieldConfig {
  fun frozen(): Boolean?
  fun validationAliases(): List<String>
}
