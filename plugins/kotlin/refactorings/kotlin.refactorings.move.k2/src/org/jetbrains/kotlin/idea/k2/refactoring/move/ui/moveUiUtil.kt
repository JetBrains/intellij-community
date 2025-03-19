// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import org.jetbrains.kotlin.idea.KotlinLanguage

internal fun String.isValidKotlinFile(): Boolean {
    return endsWith(KotlinLanguage.INSTANCE.associatedFileType?.defaultExtension ?: return false) || endsWith(".kts")
}