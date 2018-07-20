/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jetbrains.completion.feature.impl


class CompletionFactors(proximity: Set<String>, relevance: Set<String>) {

    private val knownFactors: Set<String> = HashSet<String>().apply {
        addAll(proximity.map { "prox_$it" })
        addAll(relevance)
    }

    fun unknownFactors(factors: Set<String>): List<String> {
        var result: MutableList<String>? = null
        for (factor in factors) {
            val normalized = factor.substringBefore('@')
            if (normalized !in knownFactors) {
                result = (result ?: mutableListOf()).apply { add(normalized) }
            }
        }

        return if (result != null) result else emptyList()
    }
}
