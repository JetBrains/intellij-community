// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.jetbrains.python.packaging.PyVersionSpecifiers
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PyVersionSpecifiersTest {

  @ParameterizedTest(name = "\"{0}\".isValid(\"{1}\") == {2}")
  @CsvSource(
    // Unsupported versions (not in LanguageLevel.SUPPORTED_LEVELS) are rejected
    "==3.5,  3.5.0,  false",
    ">=3.5,  3.5.5,  false",
    ">3.0,   3.5.0,  false",
    // Supported 2.7 still obeys constraints
    "<3.8,   2.7.0,  true",
    "<=3.8,  2.7.5,  true",
    ">=3.8,  2.7.0,  false",

    // Less: version < constraint
    "<3.8,   3.6.0,  true",
    "<3.8,   3.7.5,  true",
    "<3.8,   3.8.0,  false",
    "<3.8,   3.9.0,  false",
    "<3.8.5, 3.7.0,  true",
    "<3.8.5, 3.8.0,  true",
    "<3.8.5, 3.8.5,  false",
    "<3.8.5, 3.9.0,  false",

    // Less or equal: version <= constraint
    "<=3.8,  3.7.0,  true",
    "<=3.8,  3.8.0,  true",
    "<=3.8,  3.8.5,  true",
    "<=3.8,  3.9.0,  false",
    "<=3.8.5, 3.8.5, true",
    "<=3.8.5, 3.9.0, false",

    // PEP 440 exact match (==): missing components match any subversion
    "==3.8,   3.7.0,  false",
    "==3.8,   3.8.0,  true",
    "==3.8,   3.8.5,  true",
    "==3.8,   3.9.0,  false",
    "==3.8.5, 3.8.0,  false",
    "==3.8.5, 3.8.5,  true",
    "==3.8.5, 3.9.0,  false",

    // PEP 440 wildcard (==X.Y.*): same as ==X.Y with missing components
    "==3.8.*,  3.7.0,  false",
    "==3.8.*,  3.8.0,  true",
    "==3.8.*,  3.8.5,  true",
    "==3.8.*,  3.9.0,  false",
    "!=3.8.*,  3.8.0,  false",
    "!=3.8.*,  3.9.0,  true",

    // Single-equals shorthand (=): same as ==
    "=3.8,   3.8.0,  true",
    "=3.8,   3.8.5,  true",
    "=3.8,   3.9.0,  false",

    // PEP 440 exclusion (!=): inverse of ==
    "!=3.8,   3.7.0,  true",
    "!=3.8,   3.8.0,  false",
    "!=3.8,   3.8.5,  false",
    "!=3.8,   3.9.0,  true",
    "!=3.8.5, 3.8.0,  true",
    "!=3.8.5, 3.8.5,  false",
    "!=3.8.5, 3.9.0,  true",

    // Greater or equal: version >= constraint
    ">=3.8,  3.7.0,  false",
    ">=3.8,  3.8.0,  true",
    ">=3.8,  3.8.5,  true",
    ">=3.8,  3.9.0,  true",
    ">=3.8.5, 3.8.0, false",
    ">=3.8.5, 3.8.5, true",
    ">=3.8.5, 3.10.0, true",

    // Greater: version > constraint
    ">3.8,   3.7.0,  false",
    ">3.8,   3.8.0,  false",
    ">3.8,   3.8.5,  false",
    ">3.8,   3.9.0,  true",
    ">3.8.5, 3.8.5,  false",
    ">3.8.5, 3.9.0,  true",
    ">3.8.5, 3.11.0, true",
  )
  fun testSimpleOperators(constraint: String, version: String, expected: Boolean) {
    assertEquals(expected, PyVersionSpecifiers(constraint).isValid(version))
  }

  @ParameterizedTest(name = "\"{0}\".isValid(\"{1}\") == {2}")
  @CsvSource(
    // PEP 440 compatible release (~=): ~=X.Y → >=X.Y & <(X+1).0; ~=X.Y.Z → >=X.Y.Z & <X.(Y+1).0
    "~=3.8,   3.7.0,  false",
    "~=3.8,   3.8.0,  true",
    "~=3.8,   3.9.0,  true",
    "~=3.8,   3.99.0, true",
    "~=3.8,   4.0.0,  false",
    "~=3.8.5, 3.8.4,  false",
    "~=3.8.5, 3.8.5,  true",
    "~=3.8.5, 3.8.9,  true",
    "~=3.8.5, 3.9.0,  false",

    // Poetry tilde (~): ~X.Y → >=X.Y & <X.(Y+1); ~X.Y.Z → >=X.Y.Z & <X.(Y+1).0
    "~3.8,   3.7.0,  false",
    "~3.8,   3.8.0,  true",
    "~3.8,   3.8.9,  true",
    "~3.8,   3.9.0,  false",
    "~3.8.5, 3.8.4,  false",
    "~3.8.5, 3.8.5,  true",
    "~3.8.5, 3.8.9,  true",
    "~3.8.5, 3.9.0,  false",

    // Poetry caret (^): ^X.Y → >=X.Y & <(X+1).0 for X>0
    "^3.8,   3.7.0,  false",
    "^3.8,   3.8.0,  true",
    "^3.8,   3.8.5,  true",
    "^3.8,   3.9.0,  true",
    "^3.8,   3.99.0, true",
    "^3.8,   4.0.0,  false",
    "^3.8.5, 3.8.0,  false",
    "^3.8.5, 3.8.5,  true",
    "^3.8.5, 3.9.0,  true",
    "^3.8.5, 4.0.0,  false",
  )
  fun testCompoundOperators(constraint: String, version: String, expected: Boolean) {
    assertEquals(expected, PyVersionSpecifiers(constraint).isValid(version))
  }

  @ParameterizedTest(name = "\"{0}\".isValid(\"{1}\") == {2}")
  @CsvSource(
    // Strict range: >3.8,<3.10
    "'>3.8,<3.10',  3.7.0,  false",
    "'>3.8,<3.10',  3.8.0,  false",
    "'>3.8,<3.10',  3.9.0,  true",
    "'>3.8,<3.10',  3.9.5,  true",
    "'>3.8,<3.10',  3.10.0, false",
    "'>3.8,<3.10',  3.11.0, false",

    // Inclusive range: >=3.8,<=3.10
    "'>=3.8,<=3.10', 3.7.0,  false",
    "'>=3.8,<=3.10', 3.8.0,  true",
    "'>=3.8,<=3.10', 3.8.5,  true",
    "'>=3.8,<=3.10', 3.9.0,  true",
    "'>=3.8,<=3.10', 3.10.0, true",
    "'>=3.8,<=3.10', 3.10.5, true",
    "'>=3.8,<=3.10', 3.11.0, false",

    // Exclusion in range: >=3.8,!=3.9
    "'>=3.8,!=3.9',  3.8.0,  true",
    "'>=3.8,!=3.9',  3.9.0,  false",
    "'>=3.8,!=3.9',  3.9.5,  false",
    "'>=3.8,!=3.9',  3.10.0, true",

    // Three constraints: >=3.8,!=3.9,<3.12
    "'>=3.8,!=3.9,<3.12', 3.7.0,  false",
    "'>=3.8,!=3.9,<3.12', 3.8.0,  true",
    "'>=3.8,!=3.9,<3.12', 3.9.0,  false",
    "'>=3.8,!=3.9,<3.12', 3.10.0, true",
    "'>=3.8,!=3.9,<3.12', 3.12.0, false",

    // Spaces between operator and version
    ">= 3.8,   3.8.0,  true",
    "> 3.8,    3.9.0,  true",
    "<= 3.10,  3.10.0, true",

    // Spaces around commas in composite
    "'>= 3.8 , < 3.10', 3.9.0,  true",
    "'>= 3.8 , < 3.10', 3.10.0, false",
  )
  fun testCompositeSpecifiers(constraint: String, version: String, expected: Boolean) {
    assertEquals(expected, PyVersionSpecifiers(constraint).isValid(version))
  }

  @ParameterizedTest(name = "ANY_SUPPORTED.isValid(\"{0}\") == {1}")
  @CsvSource(
    "2.7.0,  true",
    "3.6.0,  true",
    "3.8.0,  true",
    "3.12.0, true",
    "3.15.0, true",
    "2.6.0,  false",
    "3.0.0,  false",
    "3.5.0,  false",
  )
  fun testAnySupportedVersion(version: String, expected: Boolean) {
    assertEquals(expected, PyVersionSpecifiers.ANY_SUPPORTED.isValid(version))
  }

  @Test
  fun testSupportedLanguageLevels() {
    val specifiers = PyVersionSpecifiers(">=3.8")
    for (level in LanguageLevel.SUPPORTED_LEVELS) {
      assertEquals(level.isAtLeast(LanguageLevel.PYTHON38), specifiers.isValid(level),
                   ">=3.8 should ${if (level.isAtLeast(LanguageLevel.PYTHON38)) "accept" else "reject"} $level")
    }
  }

  @Test
  fun testUnsupportedLanguageLevels() {
    val unsupported = LanguageLevel.entries.filter { it !in LanguageLevel.SUPPORTED_LEVELS }
    for (level in unsupported) {
      assertFalse(PyVersionSpecifiers.ANY_SUPPORTED.isValid(level),
                  "ANY_SUPPORTED should reject unsupported $level")
    }
  }

  @Test
  fun testAnySupportedAcceptsAllSupportedLevels() {
    for (level in LanguageLevel.SUPPORTED_LEVELS) {
      assertTrue(PyVersionSpecifiers.ANY_SUPPORTED.isValid(level),
                 "ANY_SUPPORTED should accept supported $level")
    }
  }

  @ParameterizedTest(name = "\"{0}\".isValid(\"{1}\") == false")
  @CsvSource(
    ">=3.8, ''",
    ">=3.8, ' '",
    ">=3.8, abc",
  )
  fun testInvalidVersionString(constraint: String, version: String) {
    assertEquals(false, PyVersionSpecifiers(constraint).isValid(version))
  }
}
