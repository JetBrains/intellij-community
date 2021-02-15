package com.jetbrains.packagesearch.intellij.plugin

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.packagesearch.intellij.plugin.api.model.PlatformTarget
import com.jetbrains.packagesearch.intellij.plugin.api.model.PlatformType
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Platform
import org.apache.commons.lang3.StringUtils

@Suppress("unused", "TooGenericExceptionCaught") // T is used to get the logger
inline fun <reified T> T.tryDoing(a: () -> Unit) = try {
    a()
} catch (t: Throwable) {
    @Suppress("TooGenericExceptionCaught") // Guarding against random runtime failures
    try {
        Logger.getInstance(T::class.java).error("Failed to dispose: ${t.message}", t)
    } catch (t: Throwable) {
        // IntelliJ IDEA Logger may throw exception that we try to log inside
    }
}

fun looksLikeGradleVariable(version: String) = version.startsWith("$")

fun List<StandardV2Platform>.asListOfTags(): List<String> = this
    .filter { it.type != null && it.type != PlatformType.UNSUPPORTED }
    .sortedBy { it.type }
    .flatMap { platform ->
        val targetPlatforms = platform.targets?.filter { it != PlatformTarget.UNSUPPORTED }
            ?.map { it.toString() }

        if (!targetPlatforms.isNullOrEmpty()) {
            targetPlatforms
        } else {
            listOf(platform.type.toString())
        }
    }

/**
 * Delegates to [org.apache.commons.lang3.StringUtils#normalizeSpace], but annotates the result
 * as safe for Nls, as the changes are not impacting i18n
 */
@NlsSafe
internal fun String?.normalizeSpace() = StringUtils.normalizeSpace(this)
