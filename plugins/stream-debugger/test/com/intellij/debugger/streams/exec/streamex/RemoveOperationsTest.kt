// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.streamex

/**
 * @author Shumaf.Lovpache
 */
open class RemoveOperationsTest : StreamExTestCase() {
  override val packageName: String = "filtering"

  fun testRemove() = doStreamExVoidTest()
  fun testRemoveBy() = doStreamExVoidTest()

  fun testRemoveKeys() = doStreamExWithResultTest()
  fun testRemoveValues() = doStreamExWithResultTest()
  fun testRemoveKeyValue() = doStreamExWithResultTest()
}