// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.console.gutter

import com.intellij.openapi.editor.markup.GutterIconRenderer
import org.jetbrains.kotlin.KotlinIdeaReplBundle
import org.jetbrains.kotlin.console.SeverityDetails
import org.jetbrains.kotlin.diagnostics.Severity

internal class ConsoleErrorRenderer(private val messages: List<SeverityDetails>) : GutterIconRenderer() {
    private fun msgType(severity: Severity) = when (severity) {
        Severity.ERROR -> KotlinIdeaReplBundle.message("message.type.error")
        Severity.WARNING -> KotlinIdeaReplBundle.message("message.type.warning")
        Severity.INFO -> KotlinIdeaReplBundle.message("message.type.info")
    }

    override fun getTooltipText(): String {
        val htmlTooltips = messages.map { "<b>${msgType(it.severity)}</b> ${it.description}" }
        @Suppress("HardCodedStringLiteral")
        return "<html>${htmlTooltips.joinToString("<hr size=1 noshade>")}</html>"
    }

    override fun getIcon() = ReplIcons.COMPILER_ERROR
    override fun hashCode() = System.identityHashCode(this)
    override fun equals(other: Any?) = this === other
}