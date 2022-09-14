/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.api.query

abstract class SearchQueryParser {

    var searchQuery: String? = null

    protected fun parse(query: String) {
        val words = splitQuery(query)
        val size = words.size

        if (size == 0) {
            return
        }
        if (size == 1) {
            addToSearchQuery(words[0])
            return
        }

        var index = 0
        while (index < size) {
            var name = words[index++]
            if (name.endsWith(":")) {
                if (index < size) {
                    val invert = name.startsWith("-")
                    name = name.substring(if (invert) 1 else 0, name.length - 1)
                    handleAttribute(name, words[index++], invert)
                } else {
                    addToSearchQuery(query)
                    return
                }
            } else {
                addToSearchQuery(name)
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected fun addToSearchQuery(query: String) {
        if (searchQuery == null) {
            searchQuery = query
        } else {
            searchQuery += " $query"
        }
    }

    protected abstract fun handleAttribute(name: String, value: String, invert: Boolean)

    @Suppress("ComplexMethod") // Adopted code
    private fun splitQuery(query: String): List<String> {
        val words = mutableListOf<String>()

        val length = query.length
        var index = 0

        while (index < length) {
            val startCh = query[index++]
            if (startCh == ' ') {
                continue
            }
            if (startCh == '"') {
                val end = query.indexOf('"', index)
                if (end == -1) {
                    break
                }
                words.add(query.substring(index, end))
                index = end + 1
                continue
            }

            val start = index - 1
            while (index < length) {
                val nextCh = query[index++]
                if (nextCh == ':' || nextCh == ' ' || index == length) {
                    words.add(query.substring(start, if (nextCh == ' ') index - 1 else index))
                    break
                }
            }
        }

        if (words.isEmpty() && length > 0) {
            words.add(query)
        }

        return words
    }
}
