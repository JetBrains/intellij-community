// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.tree

import org.jetbrains.kotlin.utils.SmartList


class JKComment(val text: String, val indent: String? = null) {
    val isSingleline
        get() = text.startsWith("//")
}

class JKTokenElementImpl(override val text: String) : JKTokenElement {
    override val trailingComments: MutableList<JKComment> = SmartList()
    override val leadingComments: MutableList<JKComment> = SmartList()
    override var hasTrailingLineBreak: Boolean = false
    override var hasLeadingLineBreak: Boolean = false
}

interface JKFormattingOwner {
    val trailingComments: MutableList<JKComment>
    val leadingComments: MutableList<JKComment>
    var hasTrailingLineBreak: Boolean
    var hasLeadingLineBreak: Boolean
}

fun <T : JKFormattingOwner> T.withFormattingFrom(other: JKFormattingOwner): T = also {
    trailingComments += other.trailingComments
    leadingComments += other.leadingComments
    hasTrailingLineBreak = other.hasTrailingLineBreak
    hasLeadingLineBreak = other.hasLeadingLineBreak
}

fun <T, S> T.withPsiAndFormattingFrom(
    other: S
): T where T : JKFormattingOwner, T : PsiOwner, S : JKFormattingOwner, S : PsiOwner = also {
    withFormattingFrom(other)
    this.psi = other.psi
}


inline fun <reified T : JKFormattingOwner> List<T>.withFormattingFrom(other: JKFormattingOwner): List<T> = also {
    if (isNotEmpty()) {
        it.first().trailingComments += other.trailingComments
        it.first().hasTrailingLineBreak = other.hasTrailingLineBreak
        it.last().leadingComments += other.leadingComments
        it.last().hasLeadingLineBreak = other.hasLeadingLineBreak
    }
}

fun JKFormattingOwner.clearFormatting() {
    trailingComments.clear()
    leadingComments.clear()
    hasTrailingLineBreak = false
    hasLeadingLineBreak = false
}

interface JKTokenElement : JKFormattingOwner {
    val text: String
}

fun JKFormattingOwner.containsNewLine(): Boolean =
    hasTrailingLineBreak || hasLeadingLineBreak