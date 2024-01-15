// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.printing

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.j2k.Nullability
import org.jetbrains.kotlin.nj2k.JKElementInfoStorage
import org.jetbrains.kotlin.nj2k.JKImportStorage
import org.jetbrains.kotlin.nj2k.symbols.JKSymbol
import org.jetbrains.kotlin.nj2k.tree.JKLambdaExpression
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.types.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal open class JKPrinterBase {
    private val stringBuilder: StringBuilder = StringBuilder()
    var currentIndent = 0
    private val indentSymbol = " ".repeat(4)
    var lastSymbolIsLineBreak = false
    private var lastSymbolIsSingleSpace = false

    override fun toString(): String = stringBuilder.toString()

    fun printWithSurroundingSpaces(value: String) {
        print(" ")
        print(value)
        print(" ")
    }

    fun print(value: String) {
        if (value.isEmpty()) return

        if (value == " ") {
            // To prettify the printed code for easier debugging, don't try to print multiple single spaces in a row
            val prevLastSymbolIsSingleSpace = lastSymbolIsSingleSpace
            lastSymbolIsSingleSpace = true
            if (!prevLastSymbolIsSingleSpace) {
                append(" ")
            }
        } else {
            lastSymbolIsSingleSpace = false
            append(value)
        }
    }

    fun println(lineBreaks: Int = 1) {
        lastSymbolIsSingleSpace = false
        repeat(lineBreaks) { append("\n") }
    }

    inline fun indented(block: () -> Unit) {
        currentIndent++
        block()
        currentIndent--
    }

    inline fun block(body: () -> Unit) {
        par(ParenthesisKind.CURVED) {
            indented(body)
        }
    }

    inline fun par(kind: ParenthesisKind = ParenthesisKind.ROUND, body: () -> Unit) {
        print(kind.open)
        body()
        print(kind.close)
    }

    inline fun <T> renderList(list: List<T>, separator: String = ", ", renderElement: (T) -> Unit) =
        renderList(list, { this.print(separator) }, renderElement)

    inline fun <T> renderList(list: List<T>, separator: () -> Unit, renderElement: (T) -> Unit) {
        if (list.isEmpty()) return
        renderElement(list.first())
        for (element in list.subList(1, list.size)) {
            separator()
            renderElement(element)
        }
    }

    private fun append(text: String) {
        if (lastSymbolIsLineBreak) {
            stringBuilder.append(indentSymbol.repeat(currentIndent))
        }
        stringBuilder.append(text)

        lastSymbolIsLineBreak = stringBuilder.lastOrNull() == '\n'
    }

    enum class ParenthesisKind(val open: String, val close: String) {
        ROUND("(", ")"),
        CURVED("{", "}"),
        ANGLE("<", ">")
    }
}

internal class JKPrinter(
    project: Project,
    importStorage: JKImportStorage,
    private val elementInfoStorage: JKElementInfoStorage
) : JKPrinterBase() {
    private val symbolRenderer = JKSymbolRenderer(importStorage, project)

    private fun JKType.renderTypeInfo() {
        this@JKPrinter.print(elementInfoStorage.getOrCreateInferenceLabelForElement(this).render())
    }

    fun renderType(type: JKType, owner: JKTreeElement? = null) {
        if (type is JKNoType) return
        if (type is JKCapturedType) {
            when (val wildcard = type.wildcardType) {
                is JKVarianceTypeParameterType -> {
                    renderType(wildcard.boundType, owner)
                }

                is JKStarProjectionType -> {
                    type.renderTypeInfo()
                    this.print("Any?")
                }
            }
            return
        }
        type.renderTypeInfo()
        when (type) {
            is JKClassType -> {
                renderSymbol(type.classReference, owner)
            }

            is JKContextType -> return
            is JKStarProjectionType ->
                this.print("*")

            is JKTypeParameterType ->
                this.print(type.identifier.name)

            is JKVarianceTypeParameterType -> {
                when (type.variance) {
                    JKVarianceTypeParameterType.Variance.IN -> this.print("in ")
                    JKVarianceTypeParameterType.Variance.OUT -> this.print("out ")
                }
                renderType(type.boundType)
            }

            else -> this.print("Unit /* TODO: ${type::class} */")
        }
        if (type is JKParametrizedType && type.parameters.isNotEmpty()) {
            par(ParenthesisKind.ANGLE) {
                renderList(type.parameters, renderElement = { renderType(it) })
            }
        }
        // we print undefined types as nullable because we need smartcast to work in nullability inference in post-processing
        if (type !is JKWildCardType
            && (type.nullability == Nullability.Default
                    && owner?.safeAs<JKLambdaExpression>()?.functionalType?.type != type
                    || type.nullability == Nullability.Nullable)
        ) {
            this.print("?")
        }
    }

    fun renderSymbol(symbol: JKSymbol, owner: JKTreeElement?) {
        print(symbolRenderer.renderSymbol(symbol, owner))
    }
}