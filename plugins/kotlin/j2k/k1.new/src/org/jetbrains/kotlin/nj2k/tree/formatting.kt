// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.tree

import org.jetbrains.kotlin.utils.SmartList

internal class JKComment(val text: String, val indent: String? = null) {
    val isSingleLine: Boolean = text.startsWith("//")
}

internal class JKTokenElementImpl(override val text: String) : JKTokenElement {
    override val commentsBefore: MutableList<JKComment> = SmartList()
    override val commentsAfter: MutableList<JKComment> = SmartList()
    override var lineBreaksBefore: Int = 0
    override var lineBreaksAfter: Int = 0
}

internal interface JKFormattingOwner {
    val commentsBefore: MutableList<JKComment>
    val commentsAfter: MutableList<JKComment>
    var lineBreaksBefore: Int
    var lineBreaksAfter: Int
}

internal val JKFormattingOwner.hasLineBreakBefore: Boolean
    get() = lineBreaksBefore > 0

internal val JKFormattingOwner.hasLineBreakAfter: Boolean
    get() = lineBreaksAfter > 0

internal fun <T : JKFormattingOwner> T.withCommentsFrom(other: JKFormattingOwner): T = also {
    commentsBefore += other.commentsBefore
    commentsAfter += other.commentsAfter
}

internal fun <T : JKFormattingOwner> T.withFormattingFrom(other: JKFormattingOwner): T = also {
    withCommentsFrom(other)
    lineBreaksBefore = other.lineBreaksBefore
    lineBreaksAfter = other.lineBreaksAfter
}

internal fun <T, S> T.withPsiAndFormattingFrom(
    other: S
): T where T : JKFormattingOwner, T : PsiOwner, S : JKFormattingOwner, S : PsiOwner = also {
    withFormattingFrom(other)
    this.psi = other.psi
}

internal inline fun <reified T : JKFormattingOwner> List<T>.withFormattingFrom(other: JKFormattingOwner): List<T> = also {
    if (isNotEmpty()) {
        it.first().commentsBefore += other.commentsBefore
        it.first().lineBreaksBefore = other.lineBreaksBefore
        it.last().commentsAfter += other.commentsAfter
        it.last().lineBreaksAfter = other.lineBreaksAfter
    }
}

internal fun JKFormattingOwner.clearFormatting() {
    commentsBefore.clear()
    commentsAfter.clear()
    lineBreaksBefore = 0
    lineBreaksAfter = 0
}

internal fun <T : JKFormattingOwner> T.takeFormattingFrom(other: JKFormattingOwner): T = also {
    withFormattingFrom(other)
    other.clearFormatting()
}

internal interface JKTokenElement : JKFormattingOwner {
    val text: String
}

internal fun JKFormattingOwner.containsNewLine(): Boolean =
    hasLineBreakBefore || hasLineBreakAfter