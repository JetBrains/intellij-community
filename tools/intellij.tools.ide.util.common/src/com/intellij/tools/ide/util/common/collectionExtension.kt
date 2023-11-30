package com.intellij.tools.ide.util.common

/**
 * [a, b, c] | [a, c, d] => [b, d]
 */
fun <T> Iterable<T>.symmetricDiff(other: Iterable<T>): Set<T> {
  val firstSet = this.toSet()
  val otherSet = other.toSet()
  return firstSet.subtract(otherSet).union(otherSet.subtract(firstSet))
}

/**
 * [a, b, c] | [a, c, d] => [b, d]
 */
fun <K, V> Map<K, V>.symmetricDiffOfKeys(other: Map<K, V>): Set<K> {
  val firstKeys = this.keys
  val otherKeys = other.keys
  return firstKeys.symmetricDiff(otherKeys).toSet()
}

/** @return Set of intersected keys */
fun <K, V> Map<K, V>.intersectKeys(other: Map<K, V>): Set<K> = this.keys.intersect(other.keys)
