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

package com.intellij.completion.enhancer

import com.intellij.lang.Language
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.stats.tracking.IntervalCounter

@State(name = "CompletionTimeStatistics", storages=arrayOf(Storage("completion.time.statistics")))
class ContributorsTimeStatistics : PersistentStateComponent<CompletionTimeStats> {

    private val completionIntervals = HashMap<Language, IntervalCounter>()
    private val secondCompletionIntervals = HashMap<Language, IntervalCounter>()

    override fun loadState(state: CompletionTimeStats) {
        completionIntervals.clear()
        secondCompletionIntervals.clear()

        val intervals = state.completionIntervals
                .mapKeys { Language.findLanguageByID(it.key)!! }
                .mapValues { IntervalCounter(MIN_POWER, MAX_POWER, EXPONENT, it.value) }


        val secondIntervals = state.secondCompletionIntervals
                .mapKeys { Language.findLanguageByID(it.key)!! }
                .mapValues { IntervalCounter(MIN_POWER, MAX_POWER, EXPONENT, it.value) }

        completionIntervals.putAll(intervals)
        secondCompletionIntervals.putAll(secondIntervals)
    }

    override fun getState(): CompletionTimeStats {
        val completionIntervalArrays = completionIntervals
                .mapKeys { it.key.id }
                .mapValues { it.value.data }

        val secondIntervalArrays = secondCompletionIntervals
                .mapKeys { it.key.id }
                .mapValues { it.value.data }

        return CompletionTimeStats().apply {
            completionIntervals = completionIntervalArrays
            secondCompletionIntervals = secondIntervalArrays
        }
    }

    fun languages() = (completionIntervals.keys + secondCompletionIntervals.keys).toList()

    fun intervals(languge: Language) = completionIntervals[languge]
    fun secondCompletionIntervals(languge: Language) = secondCompletionIntervals[languge]

    fun registerCompletionContributorsTime(languge: Language, timeTaken: Long) {
        val interval = completionIntervals[languge] ?: IntervalCounter(MIN_POWER, MAX_POWER, EXPONENT)
        interval.register(timeTaken)
        completionIntervals[languge] = interval
    }

    fun registerSecondCompletionContributorsTime(languge: Language, timeTaken: Long) {
        val interval = secondCompletionIntervals[languge] ?: IntervalCounter(MIN_POWER, MAX_POWER, EXPONENT)
        interval.register(timeTaken)
        secondCompletionIntervals[languge] = interval
    }

    companion object {
        private val MIN_POWER = 7
        private val MAX_POWER = 17
        private val EXPONENT = 2.0

        fun getInstance() = service<ContributorsTimeStatistics>()
    }

}

class CompletionTimeStats {
    @JvmField var completionIntervals: Map<String, Array<Int>> = HashMap()
    @JvmField var secondCompletionIntervals: Map<String, Array<Int>> = HashMap()
}
