// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.python.requirements.PyPackageVersionNormalizer
import java.math.BigInteger
import java.util.Locale

internal val PIXI_CONDA_VERSION_COMPARATOR: Comparator<String> = Comparator { first, second ->
  val left = parseCondaVersion(first)
  val right = parseCondaVersion(second)
  when {
    left == null && right == null -> first.compareTo(second)
    left == null -> -1
    right == null -> 1
    else -> left.compareTo(right)
  }
}

internal fun isPixiNativeVersionSelectable(version: String): Boolean {
  val normalized = PyPackageVersionNormalizer.normalize(version)?.presentableText ?: return false
  return PIXI_CONDA_VERSION_COMPARATOR.compare(version, normalized) == 0
}

private class CondaVersion(
  val epoch: BigInteger,
  val release: List<List<CondaVersionToken>>,
  val local: List<List<CondaVersionToken>>,
) : Comparable<CondaVersion> {
  override fun compareTo(other: CondaVersion): Int {
    val epochOrder = epoch.compareTo(other.epoch)
    if (epochOrder != 0) return epochOrder
    val releaseOrder = compareSegments(release, other.release)
    return if (releaseOrder != 0) releaseOrder else compareSegments(local, other.local)
  }
}

private sealed interface CondaVersionToken {
  val order: Int

  data class Number(val value: BigInteger) : CondaVersionToken {
    override val order: Int get() = 3
  }

  data class Text(val value: String) : CondaVersionToken {
    override val order: Int get() = 2
  }

  enum class Special(override val order: Int) : CondaVersionToken {
    DEV(0), SEPARATOR(1), POST(4),
  }
}

private val ZERO = CondaVersionToken.Number(BigInteger.ZERO)

private fun compareTokens(first: CondaVersionToken, second: CondaVersionToken): Int = when (first) {
  is CondaVersionToken.Number -> when (second) {
    is CondaVersionToken.Number -> first.value.compareTo(second.value)
    is CondaVersionToken.Text, is CondaVersionToken.Special -> first.order.compareTo(second.order)
  }
  is CondaVersionToken.Text -> when (second) {
    is CondaVersionToken.Text -> first.value.compareTo(second.value)
    is CondaVersionToken.Number, is CondaVersionToken.Special -> first.order.compareTo(second.order)
  }
  is CondaVersionToken.Special -> first.order.compareTo(second.order)
}

private fun compareSegments(first: List<List<CondaVersionToken>>, second: List<List<CondaVersionToken>>): Int {
  for (segment in 0 until maxOf(first.size, second.size)) {
    val left = first.getOrNull(segment).orEmpty()
    val right = second.getOrNull(segment).orEmpty()
    for (token in 0 until maxOf(left.size, right.size)) {
      val order = compareTokens(left.getOrNull(token) ?: ZERO, right.getOrNull(token) ?: ZERO)
      if (order != 0) return order
    }
  }
  return 0
}

private fun parseCondaVersion(value: String): CondaVersion? {
  val version = value.trim().lowercase(Locale.ROOT)
  if ('_' in version && '-' in version) return null
  val epochParts = version.split('!')
  if (epochParts.size > 2) return null
  val epoch = if (epochParts.size == 1) BigInteger.ZERO else {
    val rawEpoch = epochParts.first()
    if (rawEpoch.isEmpty() || rawEpoch.any { it !in '0'..'9' }) return null
    rawEpoch.toBigInteger()
  }
  val parts = epochParts.last().split('+')
  if (parts.size > 2) return null
  val release = parseCondaVersionSegments(parts.first()) ?: return null
  val local = if (parts.size == 1) emptyList() else parseCondaVersionSegments(parts.last()) ?: return null
  return CondaVersion(epoch, release, local)
}

private fun parseCondaVersionSegments(value: String): List<List<CondaVersionToken>>? {
  if (value.isEmpty() || ('_' in value && '-' in value)) return null
  val trailingSeparator = value.last() == '_' || value.last() == '-'
  val raw = if (trailingSeparator) value.dropLast(1) else value
  if (raw.isEmpty()) return null
  val segments = raw.split('.', '_', '-')
  return segments.mapIndexed { index, segment ->
    val last = index == segments.lastIndex
    if (segment.isEmpty() && !(last && trailingSeparator)) return null
    val tokens = mutableListOf<CondaVersionToken>()
    val first = segment.firstOrNull()
    if (first == null || first !in '0'..'9') tokens.add(ZERO)
    var offset = 0
    while (offset < segment.length) {
      val start = offset
      val numeric = segment[offset] in '0'..'9'
      if (!numeric && segment[offset] !in 'a'..'z') return null
      while (offset < segment.length && if (numeric) segment[offset] in '0'..'9' else segment[offset] in 'a'..'z') offset++
      val part = segment.substring(start, offset)
      tokens.add(if (numeric) CondaVersionToken.Number(part.toBigInteger()) else when (part) {
        "dev" -> CondaVersionToken.Special.DEV
        "post" -> CondaVersionToken.Special.POST
        else -> CondaVersionToken.Text(part)
      })
    }
    if (last && trailingSeparator) tokens.add(CondaVersionToken.Special.SEPARATOR)
    tokens
  }
}
