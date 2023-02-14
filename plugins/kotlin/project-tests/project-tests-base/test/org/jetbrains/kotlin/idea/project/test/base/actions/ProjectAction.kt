// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.test.base.actions

sealed class ProjectAction {
    abstract val id: String
    abstract val filePath: String
}

data class HighlightFileAction(override val filePath: String) : ProjectAction() {
    override val id: String = "highlighting"
}

data class TypeAndAutocompleteInFileAction(
    override val filePath: String,
    val typeAfter: String,
    val textToType: String,
    val expectedLookupElements: List<String>,
    val note: String? = null
) : ProjectAction() {
    override val id: String = "completion"
}