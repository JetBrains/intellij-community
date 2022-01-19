package com.jetbrains.packagesearch.intellij.plugin

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import org.apache.commons.lang3.StringUtils

@Suppress("unused", "TooGenericExceptionCaught") // T is used to get the logger
inline fun <reified T, R> T.tryDoing(a: () -> R?): R? = try {
    a()
} catch (t: Throwable) {
    @Suppress("TooGenericExceptionCaught") // Guarding against random runtime failures
    try {
        if (t !is ProcessCanceledException) {
            Logger.getInstance(T::class.java).error("Failed to execute safe operation: ${t.message}", t)
        }
    } catch (t: Throwable) {
        // IntelliJ IDEA Logger may throw exception that we try to log inside
    }
    null
}

internal fun looksLikeGradleVariable(version: PackageVersion) = version.versionName.startsWith("$")

@NlsSafe
internal fun @receiver:NlsSafe String.normalizeWhitespace(replaceWith: Char): String {
    if (replaceWith == ' ') return normalizeWhitespace()

    return map { if (it.isWhitespace()) replaceWith else it }
        .toCharArray()
        .concatToString()
}

@NlsSafe
internal fun @receiver:NlsSafe String.normalizeNewlines(replaceWith: Char = ' '): String =
    map { if (it.isNewline()) replaceWith else it }
        .toCharArray()
        .concatToString()

private fun Char.isNewline() = this == '\n' || this == '\r'

/**
 * Delegates to [org.apache.commons.lang3.StringUtils#normalizeSpace], but annotates the result
 * as safe for Nls, as the changes are not impacting i18n
 */
@NlsSafe
internal fun @receiver:NlsSafe String?.normalizeWhitespace() = StringUtils.normalizeSpace(this)
