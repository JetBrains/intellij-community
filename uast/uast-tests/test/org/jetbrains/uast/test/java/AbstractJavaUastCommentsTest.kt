// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import org.jetbrains.uast.test.common.CommentsTestBase
import java.io.File


abstract class AbstractJavaUastCommentsTest : AbstractJavaUastTest(), CommentsTestBase {

  private fun getTestFile(testName: String, ext: String) =
    File(testDataPath, testName.substringBeforeLast('.') + '.' + ext)

  override fun getCommentsFile(testName: String) = getTestFile(testName, "comments.txt")
}