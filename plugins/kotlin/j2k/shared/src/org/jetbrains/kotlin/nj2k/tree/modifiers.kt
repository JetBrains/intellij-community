// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.tree

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.nj2k.isInterface
import org.jetbrains.kotlin.nj2k.tree.Modality.*
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.OVERRIDE
import org.jetbrains.kotlin.nj2k.tree.Visibility.PUBLIC
import org.jetbrains.kotlin.nj2k.tree.visitors.JKVisitor
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

@ApiStatus.Internal
interface Modifier {
    val text: String
}

@Suppress("unused")
@ApiStatus.Internal
enum class OtherModifier(override val text: String) : Modifier {
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

@ApiStatus.Internal
sealed class JKModifierElement : JKTreeElement()

@ApiStatus.Internal
interface JKOtherModifiersOwner : JKModifiersListOwner {
    var otherModifierElements: List<JKOtherModifierElement>
}

@ApiStatus.Internal
class JKMutabilityModifierElement(var mutability: Mutability) : JKModifierElement() {
    override fun accept(visitor: JKVisitor) = visitor.visitMutabilityModifierElement(this)
}

@ApiStatus.Internal
class JKModalityModifierElement(var modality: Modality) : JKModifierElement() {
    override fun accept(visitor: JKVisitor) = visitor.visitModalityModifierElement(this)
}

@ApiStatus.Internal
class JKVisibilityModifierElement(var visibility: Visibility) : JKModifierElement() {
    override fun accept(visitor: JKVisitor) = visitor.visitVisibilityModifierElement(this)
}

@ApiStatus.Internal
class JKOtherModifierElement(var otherModifier: OtherModifier) : JKModifierElement() {
    override fun accept(visitor: JKVisitor) = visitor.visitOtherModifierElement(this)
}

@ApiStatus.Internal
interface JKVisibilityOwner : JKModifiersListOwner {
    val visibilityElement: JKVisibilityModifierElement
}

@ApiStatus.Internal
enum class Visibility(override val text: String) : Modifier {
    PUBLIC("public"),
    INTERNAL("internal"),
    PROTECTED("protected"),
    PRIVATE("private")
}

@ApiStatus.Internal
interface JKModalityOwner : JKModifiersListOwner {
    val modalityElement: JKModalityModifierElement
}

@ApiStatus.Internal
enum class Modality(override val text: String) : Modifier {
    OPEN("open"),
    FINAL("final"),
    ABSTRACT("abstract"),
}

@ApiStatus.Internal
interface JKMutabilityOwner : JKModifiersListOwner {
    val mutabilityElement: JKMutabilityModifierElement
}

@ApiStatus.Internal
enum class Mutability(override val text: String) : Modifier {
    MUTABLE("var"),
    IMMUTABLE("val"),
    UNKNOWN("var")
}

@ApiStatus.Internal
interface JKModifiersListOwner : JKFormattingOwner

@ApiStatus.Internal
fun JKOtherModifiersOwner.elementByModifier(modifier: OtherModifier): JKOtherModifierElement? =
    otherModifierElements.firstOrNull { it.otherModifier == modifier }

@ApiStatus.Internal
fun JKOtherModifiersOwner.hasOtherModifier(modifier: OtherModifier): Boolean =
    otherModifierElements.any { it.otherModifier == modifier }

@get:ApiStatus.Internal
var JKVisibilityOwner.visibility: Visibility
    get() = visibilityElement.visibility
    set(value) {
        visibilityElement.visibility = value
    }

@get:ApiStatus.Internal
var JKMutabilityOwner.mutability: Mutability
    get() = mutabilityElement.mutability
    set(value) {
        mutabilityElement.mutability = value
    }

@get:ApiStatus.Internal
var JKModalityOwner.modality: Modality
    get() = modalityElement.modality
    set(value) {
        modalityElement.modality = value
    }


@get:ApiStatus.Internal
val JKModifierElement.modifier: Modifier
    get() = when (this) {
        is JKMutabilityModifierElement -> mutability
        is JKModalityModifierElement -> modality
        is JKVisibilityModifierElement -> visibility
        is JKOtherModifierElement -> otherModifier
    }


@ApiStatus.Internal
inline fun JKModifiersListOwner.forEachModifier(action: (JKModifierElement) -> Unit) {
    safeAs<JKVisibilityOwner>()?.visibilityElement?.let(action)
    safeAs<JKModalityOwner>()?.modalityElement?.let(action)
    safeAs<JKOtherModifiersOwner>()?.otherModifierElements?.forEach(action)
    safeAs<JKMutabilityOwner>()?.mutabilityElement?.let(action)
}

internal fun JKModifierElement.isRedundant(): Boolean {
    val owner = parent ?: return false
    val hasOverrideModifier = (owner as? JKOtherModifiersOwner)?.hasOtherModifier(OVERRIDE) == true
    val isOpenAndAbstractByDefault = owner.let {
        (it is JKClass && it.isInterface()) ||
                (it is JKDeclaration && it.parentOfType<JKClass>()?.isInterface() == true)
    }

    return when (modifier) {
        PUBLIC, FINAL -> !hasOverrideModifier
        OPEN, ABSTRACT -> isOpenAndAbstractByDefault
        else -> false
    }
}