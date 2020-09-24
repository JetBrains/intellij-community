package com.jetbrains.packagesearch.intellij.plugin.api.query

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil

abstract class SearchQueryCompletionProvider(private val handleSpace: Boolean) {

    fun buildCompletionModel(query: String, caretPosition: Int): SearchQueryCompletionModel? {
        val length = query.length

        if (length == 0) return null

        if (caretPosition < length) {
            if (query[caretPosition] == ' ' && (caretPosition == 0 || query[caretPosition - 1] == ' ')) {
                return if (handleSpace) buildAttributesCompletionModel(null, caretPosition) else null
            }
        } else if (caretPosition >= 1 && query[caretPosition - 1] == ' ') {
            return if (handleSpace) buildAttributesCompletionModel(null, caretPosition) else null
        }

        val startPosition = Ref<Int>()
        val attribute = parseAttributeInQuery(query, caretPosition, startPosition)

        return if (attribute.second == null) {
            buildAttributesCompletionModel(attribute.first, startPosition.get())
        } else {
            buildAttributeValuesCompletionModel(attribute.first, attribute.second, startPosition.get())
        }
    }

    @Suppress("NestedBlockDepth") // Adopted code
    private fun parseAttributeInQuery(query: String, endPosition: Int, startPosition: Ref<in Int>): Pair<String, String> {
        var end = endPosition
        var index = end - 1
        var value: String? = null

        while (index >= 0) {
            val ch = query[index]
            if (ch == ':') {
                value = query.substring(index + 1, end)
                startPosition.set(index + 1)
                end = index + 1
                index--
                while (index >= 0) {
                    if (query[index] == ' ') {
                        break
                    }
                    index--
                }
                break
            }
            if (ch == ' ') {
                break
            }
            index--
        }

        val name = StringUtil.trimStart(query.substring(index + 1, end), "-")

        if (startPosition.isNull) {
            startPosition.set(index + if (query[index + 1] == '-') 2 else 1)
        }

        return Pair.create(name, value)
    }

    private fun buildAttributesCompletionModel(prefix: String?, caretPosition: Int): SearchQueryCompletionModel? {
        val shouldFilter = !prefix.isNullOrBlank()

        val attributes = getAttributes().filter {
            !shouldFilter || it.startsWith(prefix!!, true)
        }.toList()

        if (attributes.isEmpty()) return null

        return SearchQueryCompletionModel(
            caretPosition,
            if (prefix != null) caretPosition + prefix.length else caretPosition,
            prefix,
            attributes,
            null
        )
    }

    private fun buildAttributeValuesCompletionModel(attribute: String, prefix: String?, caretPosition: Int): SearchQueryCompletionModel? {
        val shouldFilter = !prefix.isNullOrBlank()

        val values = getValuesFor(attribute).filter {
            !shouldFilter || it.startsWith(prefix!!, true)
        }.toList()

        if (values.isEmpty()) return null

        return SearchQueryCompletionModel(
            caretPosition,
            if (prefix != null) caretPosition + prefix.length else caretPosition,
            prefix,
            null,
            values
        )
    }

    protected abstract fun getAttributes(): List<String>

    protected abstract fun getValuesFor(attribute: String): List<String>
}
