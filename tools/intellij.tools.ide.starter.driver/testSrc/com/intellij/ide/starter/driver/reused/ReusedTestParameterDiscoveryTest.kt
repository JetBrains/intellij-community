package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReusedTestParameterDiscoveryTest {
  @Test
  fun `driver parameter is claimed`() {
    assertTrue(isReusedIdeParameter(Driver::class.java, javaClass))
  }

  @Test
  fun `an unrelated parameter is left to whoever resolves it`() {
    assertFalse(isReusedIdeParameter(String::class.java, javaClass))
  }
}
