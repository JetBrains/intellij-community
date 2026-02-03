package com.intellij.ide.starter.junit5

import com.intellij.tools.ide.util.common.partition
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// partially AI generated
class CollectionPartitionTest {
  @Test
  fun `when empty list then returns list of empty lists`() {
    val list = emptyList<Int>()
    val result = list.partition(5)
    result.size.shouldBe(5)
    result.forEach { it.shouldBeEmpty() }
  }

  @Test
  fun `when single element list then returns single element in each chunk`() {
    val list = listOf(1)
    val result = list.partition(1)
    result.size.shouldBe(1)
    result.first().size.shouldBe(1)
    result.first().first().shouldBe(list.first())
  }

  @Test
  fun `when number of chunks equal to size then all one element chunks`() {
    val list = listOf(1, 2, 3, 4)
    val result = list.partition(list.size)
    result.size.shouldBe(list.size)
    for (i in list.indices) {
      result[i].shouldContainExactly(list[i])
    }
  }

  @Test
  fun `when number of chunks greater than size then returns lists with at most one element and empty lists`() {
    val list = listOf(1, 2, 3, 4)
    val result = list.partition(6)
    result.size.shouldBe(6)
    result.flatten().sorted().shouldContainExactly(list)
    result.filter { it.isNotEmpty() }.forEach { it.size.shouldBe(1) }
    result.filter { it.isEmpty() }.size.shouldBe(2)
  }

  @Test
  fun `when number of chunks less than zero then throws exception`() {
    val list = listOf(1, 2, 3, 4)
    shouldThrowExactly<IllegalArgumentException> { list.partition(-1) }
  }

  @Test
  fun `when number of chunks zero then throws exception`() {
    val list = listOf(1, 2, 3, 4)
    shouldThrowExactly<IllegalArgumentException> { list.partition(0) }
  }

  @Test
  fun `when number of chunks is one then the whole list is one chunk`() {
    val list = listOf(1, 2, 3, 4)
    val result = list.partition(1)
    result.size.shouldBe(1)
    result.first().shouldContainExactly(list)
  }

  @Test
  fun `when number of chunks is less then list size`() {
    val list = listOf(1, 2, 3, 4, 5, 6, 7, 8)
    val result = list.partition(3)
    result.size.shouldBe(3)
    result[0].shouldContainExactly(listOf(1, 2, 3))
    result[1].shouldContainExactly(listOf(4, 5, 6))
    result[2].shouldContainExactly(listOf(7, 8))
  }

  @Test
  fun `when number of chunks is 2`() {
    val list = listOf(1, 2, 3, 4)
    val result = list.partition(2)
    result.size.shouldBe(2)
    result.first().shouldContainExactly(listOf(1, 2))
    result[1].shouldContainExactly(listOf(3, 4))
  }
}