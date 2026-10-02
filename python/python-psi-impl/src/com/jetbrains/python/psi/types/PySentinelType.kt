// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.jetbrains.python.psi.PyClass
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a sentinel object type (e.g. `SENTINEL = object()`).
 * It behaves exactly like an instance of `object`, but has a specific name and strict equality.
 */
@ApiStatus.Internal
class PySentinelType(
  val sentinelName: String,
  val qName: String,
  objectClass: PyClass,
) : PyClassTypeImpl(objectClass, false) {

  override val name: String = sentinelName

  override fun toString(): String = "PySentinelType: $sentinelName"

  override fun equals(other: Any?): Boolean {
    if (!super.equals(other)) return false
    other as PySentinelType
    return qName == other.qName
  }

  override fun hashCode(): Int = 31 * pyClass.hashCode() + qName.hashCode()
}
