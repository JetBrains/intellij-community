// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import org.jetbrains.kotlin.nj2k.KotlinJ2KK2Bundle
import java.awt.datatransfer.DataFlavor

/**
 * A marker class for [ConvertTextJavaCopyPasteProcessor],
 * so that it doesn't try to run J2K on code copied from a Kotlin file.
 */
class CopiedKotlinCode : TextBlockTransferableData {
    override fun getFlavor(): DataFlavor = DATA_FLAVOR

    companion object {
        val DATA_FLAVOR: DataFlavor = DataFlavor(CopiedKotlinCode::class.java, KotlinJ2KK2Bundle.message("copy.text.copied.kotlin.code"))
    }
}
