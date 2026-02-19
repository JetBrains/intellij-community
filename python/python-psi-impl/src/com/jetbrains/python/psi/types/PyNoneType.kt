package com.jetbrains.python.psi.types

import com.jetbrains.python.PyNames

val PyType?.isNoneType: Boolean
  get() = this is PyClassType && classQName != null && classQName in PyNames.TYPE_NONE_NAMES
