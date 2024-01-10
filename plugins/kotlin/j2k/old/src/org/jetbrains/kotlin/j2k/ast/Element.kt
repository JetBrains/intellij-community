// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.ast

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.j2k.EmptyDocCommentConverter
import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.CodeConverter

fun <TElement: Element> TElement.assignPrototype(prototype: PsiElement?, inheritance: CommentsAndSpacesInheritance = CommentsAndSpacesInheritance()): TElement {
    prototypes = if (prototype != null) listOf(PrototypeInfo(prototype, inheritance)) else listOf()
    return this
}

fun <TElement: Element> TElement.assignPrototypes(vararg prototypes: PrototypeInfo): TElement {
    this.prototypes = prototypes.asList()
    return this
}

fun <TElement: Element> TElement.assignNoPrototype(): TElement {
    prototypes = listOf()
    return this
}

fun <TElement: Element> TElement.assignPrototypesFrom(element: Element, inheritance: CommentsAndSpacesInheritance? = null): TElement {
    prototypes = element.prototypes
    if (inheritance != null) {
        prototypes = prototypes?.map { PrototypeInfo(it.element, inheritance) }
    }
    createdAt = element.createdAt
    return this
}

data class PrototypeInfo(val element: PsiElement, val commentsAndSpacesInheritance: CommentsAndSpacesInheritance)

enum class SpacesInheritance {
    NONE, BLANK_LINES_ONLY, LINE_BREAKS
}

data class CommentsAndSpacesInheritance(
        val spacesBefore: SpacesInheritance = SpacesInheritance.BLANK_LINES_ONLY,
        val commentsBefore: Boolean = true,
        val commentsAfter: Boolean = true,
        val commentsInside: Boolean = true
) {
    companion object {
        val NO_SPACES = CommentsAndSpacesInheritance(spacesBefore = SpacesInheritance.NONE)
        val LINE_BREAKS = CommentsAndSpacesInheritance(spacesBefore = SpacesInheritance.LINE_BREAKS)
    }
}

fun Element.canonicalCode(): String {
    val builder = CodeBuilder(null, EmptyDocCommentConverter)
    builder.append(this)
    return builder.resultText
}

abstract class Element {
    var prototypes: List<PrototypeInfo>? = null
        set(value) {
            // do not assign prototypes to singleton instances
            if (canBeSingleton) {
                field = listOf()
                return
            }
            field = value
        }

    protected open val canBeSingleton: Boolean
        get() = isEmpty

    var createdAt: String?
            = if (saveCreationStacktraces)
                  Exception().stackTrace.joinToString("\n")
              else
                  null

    /** This method should not be used anywhere except for CodeBuilder! Use CodeBuilder.append instead. */
    abstract fun generateCode(builder: CodeBuilder)

    open fun postGenerateCode(builder: CodeBuilder) { }

    open val isEmpty: Boolean get() = false

    object Empty : Element() {
        override fun generateCode(builder: CodeBuilder) { }
        override val isEmpty: Boolean get() = true
    }

    companion object {
        var saveCreationStacktraces = false
    }
}

// this class should never be created directly - Converter.deferredElement() should be used!
class DeferredElement<TResult : Element>(
        private val generator: (CodeConverter) -> TResult
) : Element() {

    private var result: TResult? = null

    init {
        assignNoPrototype()
    }

    // need to override it to not use isEmpty
    override val canBeSingleton: Boolean
        get() = false

    fun unfold(codeConverter: CodeConverter) {
        assert(result == null)
        result = generator(codeConverter)
    }

    override fun generateCode(builder: CodeBuilder) {
        resultNotNull.generateCode(builder)
    }

    override val isEmpty: Boolean
        get() = resultNotNull.isEmpty

    private val resultNotNull: TResult
        get() {
            assert(result != null) { "No code generated for deferred element $this. Possible reason is that it has been created directly instead of Converter.lazyElement() call." }
            return result!!
        }
}

