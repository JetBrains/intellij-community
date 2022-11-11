// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.migration

import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion

class MigrationInfo(
    val oldApiVersion: ApiVersion,
    val newApiVersion: ApiVersion,
    val oldLanguageVersion: LanguageVersion,
    val newLanguageVersion: LanguageVersion,
)

fun MigrationInfo.isLanguageVersionUpdate(untilOldVersion: LanguageVersion, sinceNewVersion: LanguageVersion): Boolean {
    return oldLanguageVersion <= untilOldVersion && newLanguageVersion >= sinceNewVersion
}