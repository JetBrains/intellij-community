// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework

import kotlinx.coroutines.delay

suspend fun assertWithinTimeout(seconds: Int, assert: suspend () -> Unit) {
  for (i in 0..seconds) {
    try {
      assert()
      return
    }
    catch (e: Throwable) {
      delay(1000)
    }
  }
  assert()
}

suspend fun assertWithinTimeout(assert: suspend () -> Unit) = assertWithinTimeout(60, assert)