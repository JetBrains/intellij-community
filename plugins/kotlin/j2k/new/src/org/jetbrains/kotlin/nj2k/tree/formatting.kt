// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.tree

import org.jetbrains.kotlin.utils.SmartList


class JKComment(val text: String, val indent: String? = null) {
    val isSingleline
        get() = text.startsWith("//")
}

class JKTokenElementImpl(override val text: String) : JKTokenElement {
    override val commentsBefore: MutableList<JKComment> = SmartList()
    override val commentsAfter: MutableList<JKComment> = SmartList()
    override var hasLineBreakBefore: Boolean = false
    override var hasLineBreakAfter: Boolean = false
}

interface JKFormattingOwner {
    val commentsBefore: MutableList<JKComment>
    val commentsAfter: MutableList<JKComment>
    var hasLineBreakBefore: Boolean
    var hasLineBreakAfter: Boolean
}

fun <T : JKFormattingOwner> T.withFormattingFrom(other: JKFormattingOwner): T = also {
    commentsBefore += other.commentsBefore
    commentsAfter += other.commentsAfter
    hasLineBreakBefore = other.hasLineBreakBefore
    hasLineBreakAfter = other.hasLineBreakAfter
}

fun <T, S> T.withPsiAndFormattingFrom(
    other: S
): T where T : JKFormattingOwner, T : PsiOwner, S : JKFormattingOwner, S : PsiOwner = also {
    withFormattingFrom(other)
    this.psi = other.psi
}


inline fun <reified T : JKFormattingOwner> List<T>.withFormattingFrom(other: JKFormattingOwner): List<T> = also {
    if (isNotEmpty()) {
        it.first().commentsBefore += other.commentsBefore
        it.first().hasLineBreakBefore = other.hasLineBreakBefore
        it.last().commentsAfter += other.commentsAfter
        it.last().hasLineBreakAfter = other.hasLineBreakAfter
    }
}

fun JKFormattingOwner.clearFormatting() {
    commentsBefore.clear()
    commentsAfter.clear()
    hasLineBreakBefore = false
    hasLineBreakAfter = false
}

interface JKTokenElement : JKFormattingOwner {
    val text: String
}

fun JKFormattingOwner.containsNewLine(): Boolean =
    hasLineBreakBefore || hasLineBreakAfter