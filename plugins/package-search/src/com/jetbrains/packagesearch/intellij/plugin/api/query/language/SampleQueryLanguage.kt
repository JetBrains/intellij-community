package com.jetbrains.packagesearch.intellij.plugin.api.query.language

import com.intellij.util.io.URLUtil
import com.jetbrains.packagesearch.intellij.plugin.api.query.SearchQueryCompletionProvider
import com.jetbrains.packagesearch.intellij.plugin.api.query.SearchQueryParser

// NOTE: This file contains a *sample* implementation for a query parser and completion provider.
// Since we do not have any server-side options for handling special queries, the IntelliJ plugin
// uses a no-op provider. However for future reference and/or testing, this sample is a useful
// artifact. Keep it around until we start using something else than the no-op version.

class SampleQueryCompletionProvider : SearchQueryCompletionProvider(true) {
    override fun getAttributes(): List<String> {
        return listOf("/onlyMpp", "/onlyStable", "/tag")
    }

    override fun getValuesFor(attribute: String): List<String> {
        if (attribute.startsWith("/onlyStable:", true)) {
            return listOf("true", "false")
        }

        if (attribute.startsWith("/onlyMpp:", true)) {
            return listOf("true", "false")
        }

        return emptyList()
    }
}

class SampleQuery(query: String) : SearchQueryParser() {
    private val tags = mutableSetOf<String>()
    private var onlyStable = true
    private var onlyMpp = false

    init {
        parse(query)
    }

    override fun handleAttribute(name: String, value: String, invert: Boolean) {
        when {
            name.equals("/tag", true) -> {
                tags.add(value)
            }
            name.equals("/onlyStable", true) -> {
                onlyStable = value.toBoolean()
            }
            name.equals("/onlyMpp", true) -> {
                onlyMpp = value.toBoolean()
            }
        }
    }

    fun buildQueryString(): String {
        val url = StringBuilder()

        url.append("q=")
        if (!searchQuery.isNullOrEmpty()) {
            url.append(URLUtil.encodeURIComponent(searchQuery!!))
        }

        url.append("&onlyStable=$onlyStable")
        url.append("&onlyMpp=$onlyMpp")

        for (tag in tags) {
            url.append("&tags=").append(URLUtil.encodeURIComponent(tag))
        }

        return url.toString()
    }
}
