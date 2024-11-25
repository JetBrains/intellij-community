// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import org.jetbrains.kotlin.nj2k.KotlinNJ2KBundle
import java.awt.datatransfer.DataFlavor

/**
 * A marker class for [ConvertTextJavaCopyPasteProcessor],
 * so that it doesn't try to run J2K on code copied from a Kotlin file.
 */
class CopiedKotlinCode : TextBlockTransferableData {
    override fun getFlavor(): DataFlavor = DATA_FLAVOR

    companion object {
        val DATA_FLAVOR: DataFlavor = DataFlavor(CopiedKotlinCode::class.java, KotlinNJ2KBundle.message("copy.text.copied.kotlin.code"))
    }
}
