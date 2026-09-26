package com.jetbrains.python

enum class ProtectionLevel {
  /**
   * `public` `__members__`
   */
  PUBLIC,

  /**
   * `_protected_members`
   */
  PROTECTED,

  /**
   * `__private_members`
   */
  PRIVATE;

  companion object {
    fun forName(name: String): ProtectionLevel = when {
      name.length > 2 && name.startsWith("__") && !name.endsWith("__") && name[2] != '_'-> PRIVATE
      name.length > 1 && name.startsWith("_") && !name.endsWith("_") && name[1] != '_' -> PROTECTED
      else -> PUBLIC
    }
  }
}