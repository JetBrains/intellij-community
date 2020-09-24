package com.jetbrains.packagesearch.intellij.plugin.api.query.language

import com.jetbrains.packagesearch.intellij.plugin.api.query.SearchQueryCompletionProvider
import com.jetbrains.packagesearch.intellij.plugin.api.query.SearchQueryParser

/** @see SampleQueryCompletionProvider for more examples */
class PackageSearchQueryCompletionProvider : SearchQueryCompletionProvider(true) {

    override fun getAttributes(): List<String> = emptyList()

    override fun getValuesFor(attribute: String): List<String> = emptyList()
}

/** @see com.jetbrains.packagesearch.intellij.plugin.api.query.language.SampleQuery for more examples */
class PackageSearchQuery(rawQuery: String) : SearchQueryParser() {

    init {
        parse(rawQuery)
    }

    override fun handleAttribute(name: String, value: String, invert: Boolean) {
        // No-op
    }
}
