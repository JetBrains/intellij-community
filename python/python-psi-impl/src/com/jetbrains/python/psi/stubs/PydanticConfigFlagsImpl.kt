package com.jetbrains.python.psi.stubs


internal class PydanticConfigFlagsImpl(
  override var populateByName: Boolean? = null,
  override var validateByName: Boolean? = null,
  override var validateByAlias: Boolean? = null
): PydanticConfigFlags
