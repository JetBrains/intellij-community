// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeMetaInfo.models

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import org.jetbrains.kotlin.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.codeMetaInfo.renderConfigurations.AbstractCodeMetaInfoRenderConfiguration
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.FileLevelHighlightingConfiguration
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingConfiguration
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.LineMarkerConfiguration

class LineMarkerCodeMetaInfo(
    override val renderConfiguration: LineMarkerConfiguration,
    val lineMarker: LineMarkerInfo<*>
) : CodeMetaInfo {
    override val start: Int
        get() = lineMarker.startOffset
    override val end: Int
        get() = lineMarker.endOffset

    override val tag: String
        get() = renderConfiguration.getTag()

    override val attributes: MutableList<String> = mutableListOf()

    override fun asString(): String = renderConfiguration.asString(this)
}

abstract class AbstractHighlightingCodeMetaInfo(
    override val renderConfiguration: AbstractCodeMetaInfoRenderConfiguration,
    val highlightingInfo: HighlightInfo
) : CodeMetaInfo {
    override val start: Int
        get() = highlightingInfo.startOffset
    override val end: Int
        get() = highlightingInfo.endOffset

    override val attributes: MutableList<String> = mutableListOf()

    override fun asString(): String = renderConfiguration.asString(this)
}

class HighlightingCodeMetaInfo(
    override val renderConfiguration: HighlightingConfiguration,
    highlightingInfo: HighlightInfo
) : AbstractHighlightingCodeMetaInfo(renderConfiguration, highlightingInfo) {
    override val tag: String
        get() = renderConfiguration.getTag()
}

class FileLevelHighlightingCodeMetaInfo(
    override val renderConfiguration: FileLevelHighlightingConfiguration,
    highlightingInfo: HighlightInfo
) : AbstractHighlightingCodeMetaInfo(renderConfiguration, highlightingInfo) {
    override val tag: String
        get() = renderConfiguration.getTag()
}