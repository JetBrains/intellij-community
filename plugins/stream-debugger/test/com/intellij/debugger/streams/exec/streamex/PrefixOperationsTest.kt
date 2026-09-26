// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec.streamex

class PrefixOperationsTest : StreamExTestCase() {
  override val packageName: String = "misc"

  fun testPrefix() = doStreamExVoidTest()
  fun testPrefixKeys() = doStreamExVoidTest()
  fun testPrefixValues() = doStreamExVoidTest()
}
