// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.pathSeparator
import com.intellij.python.community.execService.impl.processLaunchers.mergeEnvs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

private const val POSIX_SEPARATOR = ":"
private const val WINDOWS_SEPARATOR = ";"

internal class MergeEnvsTest {

  @ParameterizedTest
  @EnumSource(EelOsFamily::class)
  fun bothMapsEmpty(family: EelOsFamily) {
    assertEquals(emptyMap<String, String>(), mergeEnvs(emptyMap(), emptyMap(), family))
  }

  @ParameterizedTest
  @EnumSource(EelOsFamily::class)
  fun onlyOurEnv(family: EelOsFamily) {
    assertEquals(mapOf("FOO" to "bar"), mergeEnvs(mapOf("FOO" to "bar"), emptyMap(), family))
  }

  @ParameterizedTest
  @EnumSource(EelOsFamily::class)
  fun onlyTheirEnv(family: EelOsFamily) {
    assertEquals(mapOf("FOO" to "bar"), mergeEnvs(emptyMap(), mapOf("FOO" to "bar"), family))
  }

  @ParameterizedTest
  @EnumSource(EelOsFamily::class)
  fun noOverlap(family: EelOsFamily) {
    assertEquals(mapOf("BAR" to "baz", "FOO" to "bar"), mergeEnvs(mapOf("BAR" to "baz"), mapOf("FOO" to "bar"), family))
  }

  @ParameterizedTest
  @EnumSource(EelOsFamily::class)
  fun ourValueComesFirst(family: EelOsFamily) {
    val expected = mapOf("A" to "1${family.pathSeparator}2")
    assertEquals(expected, mergeEnvs(mapOf("A" to "1"), mapOf("A" to "2"), family))
  }

  @ParameterizedTest
  @EnumSource(EelOsFamily::class)
  fun ourNamesComeFirst(family: EelOsFamily) {
    val res = mergeEnvs(mapOf("B" to "1", "A" to "2"), mapOf("C" to "3", "A" to "4"), family)
    assertEquals(listOf("B", "A", "C"), res.keys.toList())
  }

  @ParameterizedTest
  @EnumSource(EelOsFamily::class)
  fun emptyOurValueAddsNoSeparator(family: EelOsFamily) {
    assertEquals(mapOf("A" to "x"), mergeEnvs(mapOf("A" to ""), mapOf("A" to "x"), family))
  }

  @ParameterizedTest
  @EnumSource(EelOsFamily::class)
  fun emptyTheirValueAddsNoSeparator(family: EelOsFamily) {
    assertEquals(mapOf("A" to "x"), mergeEnvs(mapOf("A" to "x"), mapOf("A" to ""), family))
  }

  @ParameterizedTest
  @EnumSource(EelOsFamily::class)
  fun nameWithOnlyEmptyValuesStays(family: EelOsFamily) {
    assertEquals(mapOf("A" to ""), mergeEnvs(mapOf("A" to ""), mapOf("A" to ""), family))
  }

  @ParameterizedTest
  @EnumSource(EelOsFamily::class)
  fun inputMapsAreNotChanged(family: EelOsFamily) {
    val ourEnv = mapOf("PATH" to "/etc", "OUR" to "1")
    val theirEnv = mapOf("PATH" to "/usr", "THEIR" to "2")
    mergeEnvs(ourEnv, theirEnv, family)
    assertEquals(mapOf("PATH" to "/etc", "OUR" to "1"), ourEnv)
    assertEquals(mapOf("PATH" to "/usr", "THEIR" to "2"), theirEnv)
  }

  @Test
  fun posixNameIsCaseSensitive() {
    val res = mergeEnvs(mapOf("PATH" to "/etc"), mapOf("PATH" to "/", "path" to "asd"), EelOsFamily.Posix)
    assertEquals(mapOf("PATH" to "/etc${POSIX_SEPARATOR}/", "path" to "asd"), res)
  }

  @Test
  fun posixKeepsTwoNamesFromOneMap() {
    val res = mergeEnvs(mapOf("PATH" to "a", "path" to "b"), emptyMap(), EelOsFamily.Posix)
    assertEquals(mapOf("PATH" to "a", "path" to "b"), res)
  }

  @Test
  fun windowsNameIsCaseInsensitive() {
    val res = mergeEnvs(mapOf("PaTH" to "c:\\"), mapOf("PATH" to "z:\\", "path" to "q:\\", "a" to "B"), EelOsFamily.Windows)
    assertEquals(mapOf("PaTH" to "c:\\${WINDOWS_SEPARATOR}z:\\${WINDOWS_SEPARATOR}q:\\", "a" to "B"), res)
  }

  /**
   * The old code only matched a name of `theirEnv` against `ourEnv`.
   * Two names in `ourEnv` alone stayed apart, and the process got two `PATH` variables.
   */
  @Test
  fun windowsJoinsTwoNamesFromOurEnv() {
    val res = mergeEnvs(mapOf("PATH" to "a", "Path" to "b"), emptyMap(), EelOsFamily.Windows)
    assertEquals(mapOf("PATH" to "a${WINDOWS_SEPARATOR}b"), res)
  }

  /**
   * The old code only matched a name of `theirEnv` against `ourEnv`.
   * Two names in `theirEnv` alone stayed apart when `ourEnv` had neither of them.
   */
  @Test
  fun windowsJoinsTwoNamesFromTheirEnv() {
    val res = mergeEnvs(mapOf("X" to "1"), mapOf("PATH" to "a", "path" to "b"), EelOsFamily.Windows)
    assertEquals(mapOf("X" to "1", "PATH" to "a${WINDOWS_SEPARATOR}b"), res)
  }

  @Test
  fun windowsKeepsTheFirstName() {
    val res = mergeEnvs(mapOf("PaTH" to "x"), mapOf("PATH" to "y"), EelOsFamily.Windows)
    assertEquals(listOf("PaTH"), res.keys.toList())
  }
}
