// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import java.awt.datatransfer.DataFlavor

class CopiedJavaCode(val fileText: String, val startOffsets: IntArray, val endOffsets: IntArray) : TextBlockTransferableData {
    override fun getFlavor(): DataFlavor = DATA_FLAVOR

    companion object {
        val DATA_FLAVOR: DataFlavor = DataFlavor(ConvertJavaCopyPasteProcessor::class.java, "class: ConvertJavaCopyPasteProcessor")
    }
}
