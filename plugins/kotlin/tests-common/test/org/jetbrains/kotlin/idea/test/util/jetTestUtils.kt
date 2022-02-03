// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("JetTestUtils")
package org.jetbrains.kotlin.idea.test.util

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.SmartFMap
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

fun String.trimTrailingWhitespaces(): String =
    this.split('\n').joinToString(separator = "\n") { it.trimEnd() }

fun String.trimTrailingWhitespacesAndAddNewlineAtEOF(): String =
        this.trimTrailingWhitespaces().let {
            result -> if (result.endsWith("\n")) result else result + "\n"
        }

fun PsiFile.findElementByCommentPrefix(commentText: String): PsiElement? =
        findElementsByCommentPrefix(commentText).keys.singleOrNull()

fun PsiFile.findElementsByCommentPrefix(prefix: String): Map<PsiElement, String> {
    var result = SmartFMap.emptyMap<PsiElement, String>()
    accept(
            object : KtTreeVisitorVoid() {
                override fun visitComment(comment: PsiComment) {
                    val commentText = comment.text
                    if (commentText.startsWith(prefix)) {
                        val parent = comment.parent
                        val elementToAdd = when (parent) {
                            is KtDeclaration -> parent
                            is PsiMember -> parent
                            else -> PsiTreeUtil.skipSiblingsForward(
                                    comment,
                                    PsiWhiteSpace::class.java, PsiComment::class.java, KtPackageDirective::class.java
                            )
                        } ?: return

                        result = result.plus(elementToAdd, commentText.substring(prefix.length).trim())
                    }
                }
            }
    )
    return result
}

val CodeInsightTestFixture.elementByOffset: PsiElement
    get() {
        return file.findElementAt(editor.caretModel.offset) ?: error("Can't find element at offset. Probably <caret> is missing.")
    }

val File.slashedPath: String
    get() = KotlinTestUtils.toSlashEndingDirPath(absolutePath)

/**
 * This Util function is needed for manual test disabling via pattern:
 * if (ignored("KT-xxx")) return
 *
 * We cannot just add return in the beginning of the test because we'll get warnings "unreachable code"
 */
fun ignored(@Suppress("UNUSED_PARAMETER") reason: String) = true