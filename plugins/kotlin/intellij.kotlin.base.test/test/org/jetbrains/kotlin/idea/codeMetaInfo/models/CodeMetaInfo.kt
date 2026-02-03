// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeMetaInfo.models

import org.jetbrains.kotlin.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.codeMetaInfo.renderConfigurations.AbstractCodeMetaInfoRenderConfiguration

class ParsedCodeMetaInfo(
    override val start: Int,
    override val end: Int,
    override val attributes: MutableList<String>,
    override val tag: String,
    val params: String? = null,
    val description: String?
) : CodeMetaInfo {
    override val renderConfiguration = ParsedCodeMetaInfoRenderConfiguration

    override fun asString(): String = renderConfiguration.asString(this)

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is CodeMetaInfo) return false
        return this.tag == other.tag && this.start == other.start && this.end == other.end
    }

    override fun hashCode(): Int {
        var result = start
        result = 31 * result + end
        result = 31 * result + tag.hashCode()
        return result
    }

    fun copy(): ParsedCodeMetaInfo {
        return ParsedCodeMetaInfo(start, end, attributes.toMutableList(), tag, params, description)
    }
}

object ParsedCodeMetaInfoRenderConfiguration : AbstractCodeMetaInfoRenderConfiguration() {
    override fun asString(codeMetaInfo: CodeMetaInfo): String {
        require(codeMetaInfo is ParsedCodeMetaInfo)
        return super.asString(codeMetaInfo) + (codeMetaInfo.description?.let { "(\"$it\")" } ?: "")
    }
}

fun <T> getCodeMetaInfo(
    objects: List<T>,
    filterMetaInfo: (CodeMetaInfo) -> Boolean,
    mapToMetaInfo: (T) -> List<CodeMetaInfo>
): List<CodeMetaInfo> {
    return objects.flatMap { mapToMetaInfo(it) }.filter(filterMetaInfo)
}