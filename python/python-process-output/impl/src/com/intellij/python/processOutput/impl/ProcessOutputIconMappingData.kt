package com.intellij.python.processOutput.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.python.processOutput.ProcessBinaryFileName
import com.intellij.python.processOutput.ProcessIcon
import com.intellij.python.processOutput.ProcessMatcher
import com.intellij.python.processOutput.ProcessOutputIconMapping

internal object ProcessOutputIconMappingData {
    private val EP_NAME =
        ExtensionPointName<ProcessOutputIconMapping>(
            "com.intellij.python.processOutput.processOutputIconMapping",
        )

    val mapping: Map<ProcessBinaryFileName, ProcessIcon>
        get() =
            EP_NAME.extensionList
                .map { it.mapping }
                .takeIf { it.isNotEmpty() }
                ?.reduce { acc, mapping -> acc + mapping }
                .let { it ?: mapOf() }

    val matchers: List<ProcessMatcher>
        get() =
            EP_NAME.extensionList
                .map { it.matchers }
                .takeIf { it.isNotEmpty() }
                ?.flatten()
                .let { it ?: listOf() }
}
