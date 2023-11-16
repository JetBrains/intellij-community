package com.jetbrains.python.tests

import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.SdksKeeper
import com.jetbrains.python.sdk.Product
import junit.framework.TestCase.assertEquals
import org.junit.Test

class SdksTest {
  @Test
  fun testLoading() {
    assert(SdksKeeper.pythonReleasesByLanguageLevel().size == 5)
  }

  @Test
  fun testPyPy() {
    val releases = SdksKeeper.pythonReleasesByLanguageLevel()[LanguageLevel.PYTHON310]
    assert(releases!!.any { it.product == Product.PyPy })
  }

  @Test
  fun testAvailableSorted() {
    assertEquals(
      setOf("3.8", "3.9", "3.10", "3.11", "3.12"),
      SdksKeeper.pythonReleasesByLanguageLevel().keys.map { it.toString() }.toSet()
    )
  }
}