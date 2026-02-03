package com.jetbrains.python

import org.jetbrains.annotations.NonNls

fun isProtected(name: @NonNls String): Boolean =
  name.length > 1 && name.startsWith("_") && !name.endsWith("_") && name[1] != '_'

fun isPrivate(name: @NonNls String): Boolean =
  name.length > 2 && name.startsWith("__") && !name.endsWith("__") && name[2] != '_'

fun isSunder(name: @NonNls String): Boolean =
  name.length > 2 && name.startsWith("_") && name.endsWith("_") && name[1] != '_' && name[name.length - 2] != '_'

fun isDunder(name: @NonNls String): Boolean =
  name.length > 4 && name.startsWith("__") && name.endsWith("__") && name[2] != '_' && name[name.length - 3] != '_'
