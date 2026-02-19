// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework

import junit.framework.TestCase

fun assertNormalizedEquals(expected: String, actual: String) {
  TestCase.assertEquals(expected.normalizeLineEndings(), actual.normalizeLineEndings())
}

private fun String.normalizeLineEndings(): String = this.replace("\r\n", "\n").replace("\r", "\n")