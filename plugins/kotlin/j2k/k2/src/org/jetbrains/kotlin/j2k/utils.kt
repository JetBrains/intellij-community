// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.CommandProcessorEx
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly

fun String.asExplicitLabel(): String? =
    Regex("""/\*~~(\w+)~~\*/""").matchEntire(this)?.groupValues?.getOrNull(1)

inline fun <T> List<T>.mutate(mutate: MutableList<T>.() -> Unit): List<T> {
    val mutableList = toMutableList()
    mutate(mutableList)
    return mutableList
}

// Examples:
//   getMyProperty -> myProperty
//   isMyProperty -> isMyProperty
fun String.asGetterName(): String? =
    takeIf { JvmAbi.isGetterName(it) }
        ?.removePrefix("get")
        ?.takeIf {
            it.isNotEmpty() && it.first().isUpperCase()
                    || it.startsWith("is") && it.length > 2 && it[2].isUpperCase()
        }?.decapitalizeAsciiOnly()
        ?.escaped()

// Example: setMyProperty -> myProperty
fun String.asSetterName(): String? =
    takeIf { JvmAbi.isSetterName(it) }
        ?.removePrefix("set")
        ?.takeIf { it.isNotEmpty() && it.first().isUpperCase() }
        ?.decapitalizeAsciiOnly()
        ?.escaped()

fun String.canBeGetterOrSetterName(): Boolean =
    asGetterName() != null || asSetterName() != null

private val KEYWORDS: Set<String> = KtTokens.KEYWORDS.types.map { (it as KtKeywordToken).value }.toSet()

fun String.escaped(): String {
    val onlyUnderscores = isNotEmpty() && this.count { it == '_' } == length
    return if (this in KEYWORDS || '$' in this || onlyUnderscores) "`$this`" else this
}

fun KtReferenceExpression.resolve(): PsiElement? =
    mainReference.resolve()

fun KtExpression.unpackedReferenceToProperty(): KtProperty? {
    val referenceExpression = when (this) {
        is KtNameReferenceExpression -> this
        is KtDotQualifiedExpression -> selectorExpression as? KtNameReferenceExpression
        else -> null
    }
    return referenceExpression?.references
        ?.firstOrNull { it is KtSimpleNameReference }
        ?.resolve() as? KtProperty
}


@ApiStatus.Internal
suspend inline fun <T> withCommandOnEdt(project: Project, crossinline action: suspend () -> T): T {
    val commandProcessor = CommandProcessor.getInstance() as CommandProcessorEx
    val token = withContext(Dispatchers.EDT) {
        writeIntentReadAction {
            commandProcessor.startCommand(
                project,
                KotlinBundle.message("action.j2k.name"),
                null,
                UndoConfirmationPolicy.REQUEST_CONFIRMATION
            )
        }
    }
    var throwable: Throwable? = null
    return try {
        withContext(Dispatchers.Default) { action() }
    }
    catch (e: Throwable) {
        throwable = e
        throw e
    }
    finally {
        if (token != null) {
            withContext(Dispatchers.EDT) {
                writeIntentReadAction {
                    commandProcessor.finishCommand(token, throwable)
                }
            }
        }
    }
}