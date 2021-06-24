/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.uast.test.common.kotlin

import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.uast.*
import org.jetbrains.uast.test.common.kotlin.FirUastTestSuffix.TXT
import org.jetbrains.uast.kotlin.internal.KotlinUElementWithComments
import org.jetbrains.uast.psi.UElementWithLocation
import org.jetbrains.uast.visitor.UastVisitor
import java.io.File

interface FirUastCommentLogTestBase : FirUastPluginSelection, FirUastFileComparisonTestBase {
    private fun getCommentsFile(filePath: String, suffix: String): File = getTestMetadataFileFromPath(filePath, "comments$suffix")

    private fun getIdenticalCommentsFile(filePath: String): File = getCommentsFile(filePath, TXT)

    private fun getPluginCommentsFile(filePath: String): File {
        val identicalFile = getIdenticalCommentsFile(filePath)
        if (identicalFile.exists()) return identicalFile
        return getCommentsFile(filePath, "$pluginSuffix$TXT")
    }

    private fun UComment.testLog(indent: String): String {
        return "UComment(${text})".replace("\n", "\n" + indent)
    }

    fun check(filePath: String, file: UFile) {
        val comments = printComments(file)

        val commentsFile = getPluginCommentsFile(filePath)
        KotlinTestUtils.assertEqualsToFile(commentsFile, comments)

        cleanUpIdenticalFile(
            commentsFile,
            getCommentsFile(filePath, "$counterpartSuffix$TXT"),
            getIdenticalCommentsFile(filePath),
            kind = "comments"
        )
    }

    private fun printComments(file: UFile): String = buildString {
        val indent = "    "
        file.accept(object : UastVisitor {
            private var level = 0

            private fun printIndent() {
                append(indent.repeat(level))
            }

            private fun renderComments(comments: List<UComment>) {
                comments.forEach { comment ->
                    printIndent()
                    appendLine(comment.testLog(indent.repeat(level)))
                }
            }


            override fun visitElement(node: UElement): Boolean {
                if (node is UDeclaration || node is UFile) {
                    printIndent()
                    appendLine("${node::class.java.simpleName}(")
                    level++
                    if (node is KotlinUElementWithComments) renderComments(node.comments)
                }
                return false
            }

            override fun afterVisitElement(node: UElement) {
                super.afterVisitElement(node)
                if (node is UDeclaration || node is UFile) {
                    level--
                    printIndent()
                    appendLine(")")
                }
            }
        })
    }
}
