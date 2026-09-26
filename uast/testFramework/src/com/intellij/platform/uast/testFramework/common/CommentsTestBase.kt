// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.uast.testFramework.common

import com.intellij.testFramework.assertEqualsToFile
import org.jetbrains.uast.UFile
import org.jetbrains.uast.asRecursiveLogString
import java.io.File


interface CommentsTestBase {
  fun getCommentsFile(testName: String): File

  fun check(testName: String, file: UFile) {
    val commentsFile = getCommentsFile(testName)

    assertEqualsToFile("UAST log tree with comments", commentsFile, file.asLogStringWithComments())
  }

  private fun UFile.asLogStringWithComments(): String =
    asRecursiveLogString { uElement -> "${uElement.asLogString()} [ ${uElement.comments.joinToString { it.text }} ]" }
}