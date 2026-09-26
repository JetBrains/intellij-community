package com.jetbrains.python.psi.types

import com.jetbrains.python.psi.PyClass

/**
 * Represents a sentinel object type (e.g. `SENTINEL = object()`).
 * It behaves exactly like an instance of `object`, but has a specific name and strict equality.
 */
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
