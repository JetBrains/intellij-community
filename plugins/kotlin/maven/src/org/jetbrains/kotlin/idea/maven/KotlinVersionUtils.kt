// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.toKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion

@ApiStatus.Internal
fun isKotlinVersionAtLeast(kotlinVersion: String?, targetVersion: LanguageVersion): Boolean {
    val parsedKotlinVersion = kotlinVersion?.let { IdeKotlinVersion.parse(it).getOrNull() } ?: return false
    val targetKotlinVersion = targetVersion.toKotlinVersion()
    return parsedKotlinVersion.kotlinVersion.isAtLeast(targetKotlinVersion.major, targetKotlinVersion.minor)
}