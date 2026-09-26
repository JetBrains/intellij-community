package com.intellij.python.processOutput

import com.intellij.python.processOutput.frontend.Filter
import com.intellij.python.processOutput.frontend.FilterActionGroupState
import com.intellij.python.processOutput.frontend.FilterItem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private class FilterActionGroupStateTest {
  @Test
  fun `default active filters should be set on state instantiation`() {
    val state = FilterActionGroupState(TestFilter)
    assertEquals(
      setOf(TestFilter.Item.FILTER1, TestFilter.Item.FILTER2),
      state.active.value
    )
  }

  @Test
  fun `get and set should work as expected`() {
    val state = FilterActionGroupState(TestFilter)

    // filters 1 and 2 should be active
    assertEquals(true, state[TestFilter.Item.FILTER1])
    assertEquals(true, state[TestFilter.Item.FILTER2])
    assertEquals(false, state[TestFilter.Item.FILTER3])
    assertEquals(false, state[TestFilter.Item.FILTER4])

    // toggling all filters
    state[TestFilter.Item.FILTER1] = false
    state[TestFilter.Item.FILTER2] = false
    state[TestFilter.Item.FILTER3] = true
    state[TestFilter.Item.FILTER4] = true

    // filters 3 and 4 should be active
    assertEquals(false, state[TestFilter.Item.FILTER1])
    assertEquals(false, state[TestFilter.Item.FILTER2])
    assertEquals(true, state[TestFilter.Item.FILTER3])
    assertEquals(true, state[TestFilter.Item.FILTER4])
  }

  private object TestFilter : Filter<TestFilter.Item> {
    enum class Item(override val title: String) : FilterItem {
      FILTER1("filter1"),
      FILTER2("filter2"),
      FILTER3("filter3"),
      FILTER4("filter4"),
    }

    override val defaultActive: Set<Item> = setOf(Item.FILTER1, Item.FILTER2)
  }
}
