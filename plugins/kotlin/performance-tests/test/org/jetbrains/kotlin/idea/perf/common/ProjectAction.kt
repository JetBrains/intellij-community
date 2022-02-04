// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.common

sealed class ProjectAction {
    abstract val id: String
    abstract val filePath: String
}

data class HighlightFile(override val filePath: String) : ProjectAction() {
    override val id: String = "highlighting"
}

data class TypeAndAutocompleteInFile(
    override val filePath: String,
    val typeAfter: String,
    val textToType: String,
    val expectedLookupElements: List<String>,
    val note: String? = null
) : ProjectAction() {
    override val id: String = "completion"
}