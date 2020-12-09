package com.jetbrains.packagesearch.intellij.plugin.api.query

class SearchQueryCompletionModel(
    val caretPosition: Int,
    val endPosition: Int,
    val prefix: String?,
    val attributes: Collection<String>?,
    val values: Collection<String>?
)
