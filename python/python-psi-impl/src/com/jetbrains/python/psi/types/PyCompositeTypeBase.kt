// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import org.jetbrains.annotations.ApiStatus

/**
 * Shared base for set-of-members [PyCompositeType]s ([PyUnionType], [PyUnsafeUnionType], [PyIntersectionType]).
 * Provides the single memoized `hashCode` + fast-path `equals`, avoiding the structural-equality
 * storm on deeply nested composite types.
 */
@ApiStatus.Internal
abstract class PyCompositeTypeBase : PyCompositeType {
  /** Members compared for equality/hashing; must be effectively immutable (the hash code is memoized from it). */
  protected abstract val memberSet: Set<PyType?>

  private val cachedHashCode: Int by lazy(LazyThreadSafetyMode.PUBLICATION) { memberSet.hashCode() }

  final override fun hashCode(): Int = cachedHashCode

  final override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false // different kinds are never equal
    other as PyCompositeTypeBase
    return cachedHashCode == other.cachedHashCode && memberSet == other.memberSet
  }
}
