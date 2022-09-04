package com.intellij.ide.starter.tests.unit

import com.intellij.ide.starter.ide.IdeProductProvider
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.Test

class IdeProductProviderTest {
  @Test
  fun listingAllIdeInfoShouldWork() {
    IdeProductProvider.getProducts().shouldNotBeEmpty()
  }
}