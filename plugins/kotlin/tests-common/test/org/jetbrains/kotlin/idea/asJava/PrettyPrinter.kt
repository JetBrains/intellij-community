// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.asJava

class PrettyPrinter(private val indentSize: Int = 2) : Appendable {
    @PublishedApi
    internal val builder: StringBuilder = StringBuilder()

    @PublishedApi
    internal var prefixesToPrint: List<String> = mutableListOf()

    @PublishedApi
    internal var indent: Int = 0

    override fun append(seq: CharSequence): Appendable = apply {
        if (seq.isEmpty()) return@apply
        printPrefixes()
        seq.split('\n').forEachIndexed { index, line ->
            if (index > 0) {
                builder.append('\n')
            }
            appendIndentIfNeeded()
            builder.append(line)
        }
    }

    override fun append(seq: CharSequence, start: Int, end: Int): Appendable = apply {
        append(seq.subSequence(start, end))
    }

    override fun append(c: Char): Appendable = apply {
        printPrefixes()
        if (c != '\n') {
            appendIndentIfNeeded()
        }
        builder.append(c)
    }

    private fun printPrefixes() {
        if (prefixesToPrint.isNotEmpty()) {
            appendIndentIfNeeded()
            prefixesToPrint.forEach { builder.append(it) }
            prefixesToPrint = mutableListOf()
        }
    }

    inline fun withIndent(block: PrettyPrinter.() -> Unit) {
        indent += 1
        block(this)
        indent -= 1
    }

    private fun appendIndentIfNeeded() {
        if (builder.isEmpty() || builder[builder.lastIndex] == '\n') {
            builder.append(" ".repeat(indentSize * indent))
        }
    }

    override fun toString(): String {
        return builder.toString()
    }

    inline operator fun invoke(print: PrettyPrinter.() -> Unit) {
        this.print()
    }
}

inline fun prettyPrint(body: PrettyPrinter.() -> Unit): String =
    PrettyPrinter().apply(body).toString()