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
