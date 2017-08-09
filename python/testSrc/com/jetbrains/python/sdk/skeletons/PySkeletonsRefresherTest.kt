/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.sdk.skeletons

import com.jetbrains.python.sdk.skeletons.DefaultPregeneratedSkeletonsProvider.getPrebuiltSkeletonsName
import com.jetbrains.python.sdk.skeletons.DefaultPregeneratedSkeletonsProvider.isApplicableZippedSkeletonsFileName
import junit.framework.TestCase

/**
 * @author traff
 */


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