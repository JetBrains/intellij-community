package com.jetbrains.python.psi.stubs


/**
 * Pydantic model configuration flags.
 *
 * For standard dataclasses, attrs, and other generic `dataclass_transform`-based classes,
 * these values are always `null`. They are only set for classes recognized as Pydantic models.
 */
interface PydanticConfigFlags {
  var populateByName: Boolean?

  var validateByName: Boolean?

  var validateByAlias: Boolean?
}
