package com.intellij.python.pyproject

import org.jetbrains.annotations.ApiStatus

/**
 * Represents a file object.
 */
@ConsistentCopyVisibility
@ApiStatus.Internal
data class PyProjectFile internal constructor(
  val name: String,
  val contentType: String? = null,
)