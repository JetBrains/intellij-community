// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.skeletons

import com.jetbrains.python.sdk.skeletons.DefaultPregeneratedSkeletonsProvider.getPrebuiltSkeletonsName
import com.jetbrains.python.sdk.skeletons.DefaultPregeneratedSkeletonsProvider.isApplicableZippedSkeletonsFileName
import junit.framework.TestCase

class PySkeletonsRefresherTest : TestCase() {

  fun testZippedSkeletonsName() {
    val fileName = getPrebuiltSkeletonsName(0, "3.6.2", true, true)
    val minorVersionAware = getPrebuiltSkeletonsName(0, "3.6.2", true, false)
    val minorVersionAgnostic = getPrebuiltSkeletonsName(0, "3.6.2", false, false)

    assertTrue(isApplicableZippedSkeletonsFileName(minorVersionAware, fileName))
    assertTrue(isApplicableZippedSkeletonsFileName(minorVersionAgnostic, fileName))
    assertFalse(isApplicableZippedSkeletonsFileName(minorVersionAgnostic,  getPrebuiltSkeletonsName(0, "3.5.2", true, true)))
  }

}