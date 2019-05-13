// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.streamex

class PartialReductionOperationsTest : StreamExTestCase() {
  override val packageName: String = "partialReduction"

  fun testGroupRuns() = doStreamExWithResultTest()
  fun testCollapse() = doStreamExVoidTest()
  fun testIntervalMap() = doStreamExVoidTest()
  fun testRunLengths() = doStreamExWithResultTest()
  fun testCollapseKeys() = doStreamExWithResultTest()
}
