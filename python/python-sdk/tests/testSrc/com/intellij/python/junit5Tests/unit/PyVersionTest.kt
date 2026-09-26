package com.intellij.python.junit5Tests.unit

import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PyVersionTest {

  @Test
  fun testVersion() {
    val pythons = mapOf(
      "Python 3.10" to LanguageLevel.PYTHON310,
      "Python 3.11.2" to LanguageLevel.PYTHON311,
      "Python 3.8.0" to LanguageLevel.PYTHON38
    )
    for ((versionString, level) in pythons) {
      val calculatedLevel = LanguageLevel.getLanguageLevelFromVersionStringStaticSafe(versionString)
      Assertions.assertEquals(level, calculatedLevel, "wrong level for $versionString")
    }
  }

  @Test
  fun testFailure() {
    Assertions.assertNull(LanguageLevel.getLanguageLevelFromVersionStringStaticSafe("ASDSD"))
  }
}