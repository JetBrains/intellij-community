// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.java

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.CommentsTestBase
import org.junit.Test

class JavaUastCommentOwnersTest : AbstractJavaUastCommentsTest(), CommentsTestBase {

  override fun check(testName: String, file: UFile) {
    super<CommentsTestBase>.check(testName, file)
  }

  @Test
  fun testCommentOwners() = doTest("Simple/CommentOwners.java")

  @Test
  fun testComments() = doTest("Simple/Comments.java")
}