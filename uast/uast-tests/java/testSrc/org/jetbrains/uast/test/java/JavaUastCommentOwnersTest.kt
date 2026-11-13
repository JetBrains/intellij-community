// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.java

import com.intellij.platform.uast.testFramework.common.CommentsTestBase
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class JavaUastCommentOwnersTest : CommentsTestBase {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val uast by javaUastFixture(projectFixture) { testName, file -> check(testName, file) }

  private fun getTestFile(testName: String, ext: String) =
    File(uast.fixture.testDataPath, testName.substringBeforeLast('.') + '.' + ext)

  override fun getCommentsFile(testName: String) = getTestFile(testName, "comments.txt")

  @Test
  fun testCommentOwners(): Unit = uast.doTest("Simple/CommentOwners.java")

  @Test
  fun testComments(): Unit = uast.doTest("Simple/Comments.java")
}