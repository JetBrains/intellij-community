// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.tree

import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.nj2k.isInterface
import org.jetbrains.kotlin.nj2k.tree.Modality.*
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.INNER
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.OVERRIDE
import org.jetbrains.kotlin.nj2k.tree.Visibility.*
import org.jetbrains.kotlin.nj2k.tree.visitors.JKVisitor
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

interface Modifier {
    val text: String
}

@Suppress("unused")
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

sealed class JKModifierElement : JKTreeElement()

interface JKOtherModifiersOwner : JKModifiersListOwner {
    var otherModifierElements: List<JKOtherModifierElement>
}

class JKMutabilityModifierElement(var mutability: Mutability) : JKModifierElement() {
    override fun accept(visitor: JKVisitor) = visitor.visitMutabilityModifierElement(this)
}

class JKModalityModifierElement(var modality: Modality) : JKModifierElement() {
    override fun accept(visitor: JKVisitor) = visitor.visitModalityModifierElement(this)
}

class JKVisibilityModifierElement(var visibility: Visibility) : JKModifierElement() {
    override fun accept(visitor: JKVisitor) = visitor.visitVisibilityModifierElement(this)
}

class JKOtherModifierElement(var otherModifier: OtherModifier) : JKModifierElement() {
    override fun accept(visitor: JKVisitor) = visitor.visitOtherModifierElement(this)
}

interface JKVisibilityOwner : JKModifiersListOwner {
    val visibilityElement: JKVisibilityModifierElement
}

enum class Visibility(override val text: String) : Modifier {
    PUBLIC("public"),
    INTERNAL("internal"),
    PROTECTED("protected"),
    PRIVATE("private")
}

interface JKModalityOwner : JKModifiersListOwner {
    val modalityElement: JKModalityModifierElement
}

enum class Modality(override val text: String) : Modifier {
    OPEN("open"),
    FINAL("final"),
    ABSTRACT("abstract"),
}

interface JKMutabilityOwner : JKModifiersListOwner {
    val mutabilityElement: JKMutabilityModifierElement
}

enum class Mutability(override val text: String) : Modifier {
    MUTABLE("var"),
    IMMUTABLE("val"),
    UNKNOWN("var")
}

interface JKModifiersListOwner : JKFormattingOwner

fun JKOtherModifiersOwner.elementByModifier(modifier: OtherModifier): JKOtherModifierElement? =
    otherModifierElements.firstOrNull { it.otherModifier == modifier }

fun JKOtherModifiersOwner.hasOtherModifier(modifier: OtherModifier): Boolean =
    otherModifierElements.any { it.otherModifier == modifier }

var JKVisibilityOwner.visibility: Visibility
    get() = visibilityElement.visibility
    set(value) {
        visibilityElement.visibility = value
    }

var JKMutabilityOwner.mutability: Mutability
    get() = mutabilityElement.mutability
    set(value) {
        mutabilityElement.mutability = value
    }

var JKModalityOwner.modality: Modality
    get() = modalityElement.modality
    set(value) {
        modalityElement.modality = value
    }


val JKModifierElement.modifier: Modifier
    get() = when (this) {
        is JKMutabilityModifierElement -> mutability
        is JKModalityModifierElement -> modality
        is JKVisibilityModifierElement -> visibility
        is JKOtherModifierElement -> otherModifier
    }


inline fun JKModifiersListOwner.forEachModifier(action: (JKModifierElement) -> Unit) {
    safeAs<JKVisibilityOwner>()?.visibilityElement?.let(action)
    safeAs<JKModalityOwner>()?.modalityElement?.let(action)
    safeAs<JKOtherModifiersOwner>()?.otherModifierElements?.forEach(action)
    safeAs<JKMutabilityOwner>()?.mutabilityElement?.let(action)
}

internal fun JKModifierElement.isRedundant(languageVersionSettings: LanguageVersionSettings): Boolean {
    val owner = parent ?: return false
    if (owner is JKMethod && owner.hasRedundantVisibility && modifier is Visibility) return true

    val hasOverrideModifier = (owner as? JKOtherModifiersOwner)?.hasOtherModifier(OVERRIDE) == true
    val hasInnerModifier = (owner as? JKOtherModifiersOwner)?.hasOtherModifier(INNER) == true
    val isOpenAndAbstractByDefault = owner.let {
        (it is JKClass && it.isInterface()) ||
                (it is JKDeclaration && it.parentOfType<JKClass>()?.isInterface() == true)
    }
    val parentClass = owner.parentOfType<JKClass>()

    val parentIsPrivate = parentClass?.visibility == PRIVATE || owner.parentOfType<JKMethod>() != null
    val redundantOnConstructor = (owner is JKConstructor && parentClass?.classKind == JKClass.ClassKind.ENUM)
            || (owner is JKKtPrimaryConstructor && parentClass?.visibility == PRIVATE && parentClass.parentOfType<JKClass>() != null)

    val isExplicitApiMode = languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) != ExplicitApiMode.DISABLED

    return when (modifier) {
        PUBLIC -> !hasOverrideModifier && !isExplicitApiMode
        FINAL -> !hasOverrideModifier
        PRIVATE -> (parentIsPrivate && owner is JKMethod && owner !is JKConstructor) || redundantOnConstructor
        INTERNAL -> (parentIsPrivate && owner is JKMethod && owner !is JKConstructor)
                || redundantOnConstructor || (hasInnerModifier && parentIsPrivate)

        OPEN, ABSTRACT -> isOpenAndAbstractByDefault

        else -> false
    }
}