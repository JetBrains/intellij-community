// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.copyPaste

/**
 * Runs J2K on the pasted code and updates the target Kotlin file as a side effect.
 * Used by [ConvertJavaCopyPasteProcessor].
 */
interface J2KCopyPasteConverter {
    fun convert()

    /**
     * This is a shortcut for copy-pasting trivial code that doesn't need to be converted (for example, a single identifier).
     * In this case, we don't bother showing a J2K dialog and only restore references / insert required imports in the Kotlin file.
     *
     * Always runs the J2K conversion once and saves the result for later reference.
     *
     * @return `true` if the conversion text remains unchanged; `false` otherwise.
     */
    fun convertAndRestoreReferencesIfTextIsUnchanged(): Boolean
}
