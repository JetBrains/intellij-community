// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

/**
 * Represents a zero-allocation integer range of [start..endExclusive).
 * 
 * Packs [start] (highest 32 bits) and [endExclusive] (lowest 32 bits).
 */
@JvmInline
internal value class PackedIntRange(
  internal val packedData: Long,
) {
  constructor(start: Int, endExclusive: Int) : this(
    // (start << 32) | (size & 0xFFFF_FFFF)
    ((start.toLong()) shl 32) or (endExclusive.toLong() and 0xFFFF_FFFFL)
  ) {
    assert(start >= 0 && endExclusive >= 0)
    assert(start <= endExclusive)
  }

  /**
   * Start of the range.
   */
  val start: Int
    get() =
      // data >> 32
      (packedData shr 32).toInt()

  /**
   * End of the range (exclusive).
   */
  val endExclusive: Int
    get() =
      // .toInt() truncates most significant 32 bits
      packedData.toInt()

  /**
   * The length of the range.
   */
  val length: Int
    inline get() =
      endExclusive - start

  override fun toString(): String =
    "$start..<$endExclusive"
}
