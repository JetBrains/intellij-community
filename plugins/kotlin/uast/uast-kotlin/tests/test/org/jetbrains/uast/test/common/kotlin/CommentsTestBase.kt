// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin.org.jetbrains.uast.test.common.kotlin

import org.jetbrains.uast.UFile
import org.jetbrains.uast.asRecursiveLogString
import org.jetbrains.uast.test.env.kotlin.assertEqualsToFile
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