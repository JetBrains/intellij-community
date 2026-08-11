// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.jetbrains.python.Result
import java.nio.ByteBuffer

/**
 * A set that is represented by a byte buffer which consists of ASCII strings sorted in alphabetical order and separated by a new line. The
 * packed representation reduces the memory footprint for large string sets. Having strings stored in alphabetical order allows binary
 * search to be utilized for lookup. Since the data structure is backed by a byte buffer, it also allows for the entire set to be memory
 * mapped.
 */
internal class PackedAsciiStringSet private constructor(
  private val byteBuffer: ByteBuffer,

  /**
   * Total number of strings stored in the set.
   */
  val size: Int,
) {
  /**
   * Checks if the specified string is contained in the set.
   * 
   * @param element the string to check for.
   * @return true if the string is contained in the set, false otherwise.
   */
  operator fun contains(element: String): Boolean {
    return ByteBuffer.wrap(element.toByteArray(CHARSET)).let { elementBuffer ->
      binarySearch { currentElement ->
        currentElement.compareTo(elementBuffer)
      } != null
    }
  }

  /**
   * Performs a prefix search over the set.
   * 
   * @param prefix the prefix to match the strings against.
   * @param pageSize the maximum amount of items permitted per page.
   * @throws IllegalArgumentException when a page size of 0 or below is passed.
   * @return result of the search, paginated.
   */
  fun searchByPrefix(prefix: String, pageSize: Int): SearchResult {
    // In order to avoid needless iteration over the entire set, this algorithm takes advantage of the sorted nature of the set. It first
    // utilizes binary search to locate an occurrence of a string that matches the prefix, then captures all matches to the left and right.

    if (pageSize <= 0) {
      throw IllegalArgumentException("Invalid page size of $pageSize")
    }

    val bufferCapacity = byteBuffer.capacity()

    // Find an occurence of a string that matches the prefix.
    val prefixBuffer = ByteBuffer.wrap(prefix.toByteArray(CHARSET))
    val foundMatchingRange = binarySearch { currentElement ->
      if (currentElement.startsWith(prefixBuffer)) {
        // If the current element starts with the prefix, 0 is returned, signifying the end of the search.
        0
      }
      else {
        // Otherwise, continue with the next step of the binary search.
        currentElement.compareTo(prefixBuffer)
      }
    }

    if (foundMatchingRange == null) {
      return SearchResult(0, emptyList())
    }

    // Calculating the left-most match.
    var firstMatchingRange: PackedIntRange = foundMatchingRange

    while (firstMatchingRange.start > 0) {
      val previousRange = this[firstMatchingRange.start - 1]

      if (previousRange.startsWith(prefixBuffer)) {
        firstMatchingRange = previousRange
      }
      else {
        break
      }
    }

    // From the left-most point, iterating over every match to the right to calculate the correct offsets for pages.
    val pagesList = mutableListOf<SearchPageIterable>()
    var currentRange = firstMatchingRange
    var currentPageItemsCount = 0
    var currentPageStartingIndex = currentRange.start
    var currentPageEndingIndex = 0
    var totalCount = 0

    while (currentRange.endExclusive < bufferCapacity) {
      currentPageItemsCount += 1
      totalCount += 1
      currentPageEndingIndex = currentRange.endExclusive + 1

      if (currentPageItemsCount >= pageSize) {
        pagesList += SearchPageIterable(currentPageStartingIndex, currentPageEndingIndex)
        currentPageStartingIndex = currentPageEndingIndex
        currentPageItemsCount = 0
      }

      if (currentPageEndingIndex >= bufferCapacity) {
        break
      }

      val nextRange = this[currentPageEndingIndex]

      if (
      // The `foundMatchingRange` variable represents the original match found earlier by the binary search. If the current range comes
      // before that it means that it already matches the prefix, so no need to call `.startsWith` for those.
        currentRange.start <= foundMatchingRange.start ||
        // Otherwise, it is currently checking matches to the right, so a `.startsWith` call is needed.
        nextRange.startsWith(prefixBuffer)
      ) {
        currentRange = nextRange
      }
      else {
        break
      }
    }

    if (currentPageItemsCount > 0) {
      pagesList += SearchPageIterable(currentPageStartingIndex, currentPageEndingIndex)
    }

    return SearchResult(totalCount, pagesList)
  }

  private fun binarySearch(comparator: (PackedIntRange) -> Int): PackedIntRange? {
    val capacity = byteBuffer.capacity()

    if (capacity == 0) {
      return null
    }

    var left = 0
    var right = capacity - 1

    while (left <= right) {
      val center = (left + right) / 2
      val currentEntry = this[center]
      val comparisonResult = comparator(currentEntry)

      when {
        comparisonResult == 0 ->
          return currentEntry
        comparisonResult > 0 ->
          right = center - 1
        else ->
          left = center + 1
      }
    }

    return null
  }

  private operator fun get(index: Int): PackedIntRange =
    getRangeAtIndex(index, byteBuffer)

  private fun PackedIntRange.startsWith(prefix: ByteBuffer): Boolean {
    val capacity = prefix.capacity()

    if (capacity > length) {
      return false
    }

    var index = 0
    while (index < capacity) {
      if (byteBuffer[start + index] != prefix[index]) {
        return false
      }

      index += 1
    }

    return true
  }

  private fun PackedIntRange.compareRangeTo(other: PackedIntRange, otherBuffer: ByteBuffer): Int =
    compareRanges(this, byteBuffer, other, otherBuffer)

  private operator fun PackedIntRange.compareTo(other: ByteBuffer): Int =
    compareRangeTo(PackedIntRange(0, other.capacity()), other)

  private inner class SearchPageIterable(
    private val startIndex: Int,
    private val finalIndex: Int,
  ) : Iterable<String> {
    override fun iterator() =
      object : Iterator<String> {
        private var currentIndex = startIndex

        override fun next(): String {
          val range = this@PackedAsciiStringSet[currentIndex]
          val buffer = ByteBuffer.allocateDirect(range.length)

          currentIndex += range.length + 1
          buffer.put(0, byteBuffer, range.start, range.length)

          return CHARSET.decode(buffer).toString()
        }

        override fun hasNext(): Boolean =
          currentIndex < finalIndex
      }
  }

  /**
   * Result of a [searchByPrefix] call.
   */
  data class SearchResult(
    /**
     * Total number of items matched.
     */
    val total: Int,

    /**
     * List of matches, paginated.
     */
    val pages: List<Iterable<String>>,
  )

  /**
   * This error is returned when a [PackedAsciiStringSet] is attempted to be created with an invalid format of the underlying buffer.
   */
  class InvalidFormatError(
    /**
     * The detailed description of the formatting issue.
     */
    val message: String,
  )

  companion object {
    private const val NEW_LINE = '\n'.code.toByte()
    private val CHARSET = Charsets.US_ASCII

    /**
     * Creates a new instance of [PackedAsciiStringSet] from a provided byte buffer.
     * 
     * @param buffer the underlying buffer representing data of the set.
     * @return an instance of [Result.Success] when a correctly-formatted buffer is provided, otherwise an instance of [Result.Failure] that 
     * wraps an instance of [InvalidFormatError].
     */
    fun create(buffer: ByteBuffer): Result<PackedAsciiStringSet, InvalidFormatError> {
      val capacity = buffer.capacity()

      if (capacity == 0) {
        return Result.Success(PackedAsciiStringSet(buffer, 0))
      }

      if (buffer[capacity - 1] != NEW_LINE) {
        return Result.Failure(InvalidFormatError("Provided buffer doesn't end with a new line"))
      }

      var currentIndex = 0
      var prevRange = PackedIntRange(0, 0)
      var finalSize = 0

      while (currentIndex < capacity) {
        val currentRange = getRangeAtIndex(currentIndex, buffer)
        currentIndex += currentRange.length + 1

        if (compareRanges(currentRange, buffer, prevRange, buffer) < 0) {
          return Result.Failure(InvalidFormatError("String at $currentRange breaks alphabetical order when compared to $prevRange"))
        }

        prevRange = currentRange
        finalSize += 1
      }

      return Result.Success(PackedAsciiStringSet(buffer, finalSize))
    }

    private fun getRangeAtIndex(index: Int, buffer: ByteBuffer): PackedIntRange {
      val capacity = buffer.capacity()

      // `index !in 0..<bufferCapacity` is not used to avoid allocating an IntRange
      if ((index < 0) || (index >= capacity)) {
        throw IndexOutOfBoundsException(index)
      }

      var start = index

      while (start > 0 && buffer[start - 1] != NEW_LINE) {
        start -= 1
      }

      var end = index

      while (buffer[end] != NEW_LINE) {
        assert(end != capacity - 1)
        end += 1
      }

      return PackedIntRange(start, end)
    }

    private fun compareRanges(lhs: PackedIntRange, lhsBuffer: ByteBuffer, rhs: PackedIntRange, rhsBuffer: ByteBuffer): Int {
      val lhsLen = lhs.length
      val rhsLen = rhs.length
      val min = lhsLen.coerceAtMost(rhsLen)
      var index = 0

      while (index < min) {
        val lhsByte = lhsBuffer[lhs.start + index]
        val rhsByte = rhsBuffer[rhs.start + index]

        if (lhsByte < rhsByte) {
          return -1
        }
        else if (lhsByte > rhsByte) {
          return 1
        }

        index += 1
      }

      return lhsLen - rhsLen
    }
  }
}