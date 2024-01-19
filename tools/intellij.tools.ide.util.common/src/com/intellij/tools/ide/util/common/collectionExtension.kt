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

/**
 * Partitions an iterable into a specific number of chunks.
 *
 * @param T the type of elements in the iterable.
 * @param numberOfChunks the number of partitions to divide the iterable into. Must be greater than 0.
 *
 * @return a list of lists which is the partitioned iterable. Each inner list is a partition of the original iterable.
 *         If the size of the iterable is less than the numberOfChunks, the additional chunks will be empty lists.
 *         If the numberOfChunks is greater than the size of the iterable, each element will be in its separate list while the remaining chunks will be empty lists.
 *
 * @throws IllegalArgumentException if numberOfChunks is less than or equal to 0.
 *
 * @sample
 *         For `listOf(1, 2, 3, 4, 5, 6).partition(4)`, the output will be `[[1,2],[3,4],[5],[6]]`.
 *         For `listOf(1, 2).partition(4)`, the output will be `[[1],[2],[],[]]`.
 */
fun <T> Iterable<T>.partition(numberOfChunks: Int): List<List<T>> {
  if (numberOfChunks <= 0) throw IllegalArgumentException("Number of chunks must be greater than 0.")

  val list: List<T> = this.toList()

  val chunked = mutableListOf<List<T>>()
  if (list.size >= numberOfChunks) {
    val chunkSize = list.size / numberOfChunks
    val remainder = list.size % numberOfChunks

    (0..<numberOfChunks).forEach { chunkIndex ->
      val from = chunkIndex * chunkSize + kotlin.math.min(chunkIndex, remainder)
      val to = from + chunkSize + if (chunkIndex < remainder) 1 else 0
      chunked.add(list.subList(from, to))
    }
  }
  else {
    chunked.addAll(list.map { listOf(it) })
    chunked.addAll(List(numberOfChunks - list.size) { emptyList() })
  }

  var elements = 0
  chunked.forEach { elements += it.size }

  assert(chunked.size == numberOfChunks) { "Collection of size ${list.size} should be split in exactly $numberOfChunks chunks" }
  assert(elements == list.size) { "Collection should be split in exactly $numberOfChunks chunks with total of ${list.size} elements" }

  return chunked
}