package com.intellij.python.pyproject

import org.jetbrains.annotations.ApiStatus

/**
 * Represents a contact. Both [name] and [email] can't be absent at the same time.
 */
@ConsistentCopyVisibility
@ApiStatus.Internal
data class PyProjectContact internal constructor(val name: String?, val email: String?) {
  init {
    if (name == null && email == null) {
      throw IllegalArgumentException("at least name or email should be provided")
    }
  }
}