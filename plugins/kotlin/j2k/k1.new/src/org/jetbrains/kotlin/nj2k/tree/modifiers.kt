// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.tree

import org.jetbrains.kotlin.nj2k.tree.visitors.JKVisitor
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal interface Modifier {
    val text: String
}

@Suppress("unused")
internal enum class OtherModifier(override val text: String) : Modifier {
    OVERRIDE("override"),
    ACTUAL("actual"),
    ANNOTATION("annotation"),
    COMPANION("companion"),
    CONST("const"),
    CROSSINLINE("crossinline"),
    DATA("data"),
    EXPECT("expect"),
    EXTERNAL("external"),
    INFIX("infix"),
    INLINE("inline"),
    INNER("inner"),
    LATEINIT("lateinit"),
    NOINLINE("noinline"),
    OPERATOR("operator"),
    OUT("out"),
    REIFIED("reified"),
    SEALED("sealed"),
    SUSPEND("suspend"),
    TAILREC("tailrec"),
    VARARG("vararg"),
    FUN("fun"),

    NATIVE("native"),
    STATIC("static"),
    STRICTFP("strictfp"),
    SYNCHRONIZED("synchronized"),
    TRANSIENT("transient"),
    VOLATILE("volatile")
}

internal sealed class JKModifierElement : JKTreeElement()

internal interface JKOtherModifiersOwner : JKModifiersListOwner {
    var otherModifierElements: List<JKOtherModifierElement>
}

internal class JKMutabilityModifierElement(var mutability: Mutability) : JKModifierElement() {
    override fun accept(visitor: JKVisitor) = visitor.visitMutabilityModifierElement(this)
}

internal class JKModalityModifierElement(var modality: Modality) : JKModifierElement() {
    override fun accept(visitor: JKVisitor) = visitor.visitModalityModifierElement(this)
}

internal class JKVisibilityModifierElement(var visibility: Visibility) : JKModifierElement() {
    override fun accept(visitor: JKVisitor) = visitor.visitVisibilityModifierElement(this)
}

internal class JKOtherModifierElement(var otherModifier: OtherModifier) : JKModifierElement() {
    override fun accept(visitor: JKVisitor) = visitor.visitOtherModifierElement(this)
}

internal interface JKVisibilityOwner : JKModifiersListOwner {
    val visibilityElement: JKVisibilityModifierElement
}

internal enum class Visibility(override val text: String) : Modifier {
    PUBLIC("public"),
    INTERNAL("internal"),
    PROTECTED("protected"),
    PRIVATE("private")
}

internal interface JKModalityOwner : JKModifiersListOwner {
    val modalityElement: JKModalityModifierElement
}

internal enum class Modality(override val text: String) : Modifier {
    OPEN("open"),
    FINAL("final"),
    ABSTRACT("abstract"),
}

internal interface JKMutabilityOwner : JKModifiersListOwner {
    val mutabilityElement: JKMutabilityModifierElement
}

internal enum class Mutability(override val text: String) : Modifier {
    MUTABLE("var"),
    IMMUTABLE("val"),
    UNKNOWN("var")
}

internal interface JKModifiersListOwner : JKFormattingOwner

internal fun JKOtherModifiersOwner.elementByModifier(modifier: OtherModifier): JKOtherModifierElement? =
    otherModifierElements.firstOrNull { it.otherModifier == modifier }

internal fun JKOtherModifiersOwner.hasOtherModifier(modifier: OtherModifier): Boolean =
    otherModifierElements.any { it.otherModifier == modifier }

internal var JKVisibilityOwner.visibility: Visibility
    get() = visibilityElement.visibility
    set(value) {
        visibilityElement.visibility = value
    }

internal var JKMutabilityOwner.mutability: Mutability
    get() = mutabilityElement.mutability
    set(value) {
        mutabilityElement.mutability = value
    }

internal var JKModalityOwner.modality: Modality
    get() = modalityElement.modality
    set(value) {
        modalityElement.modality = value
    }


internal val JKModifierElement.modifier: Modifier
    get() = when (this) {
        is JKMutabilityModifierElement -> mutability
        is JKModalityModifierElement -> modality
        is JKVisibilityModifierElement -> visibility
        is JKOtherModifierElement -> otherModifier
    }


internal inline fun JKModifiersListOwner.forEachModifier(action: (JKModifierElement) -> Unit) {
    safeAs<JKVisibilityOwner>()?.visibilityElement?.let(action)
    safeAs<JKModalityOwner>()?.modalityElement?.let(action)
    safeAs<JKOtherModifiersOwner>()?.otherModifierElements?.forEach(action)
    safeAs<JKMutabilityOwner>()?.mutabilityElement?.let(action)
}

