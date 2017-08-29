package com.intellij.completion.enhancer

import com.intellij.lang.Language
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.stats.tracking.IntervalCounter

@State(name = "CompletionTimeStatistics", storages=arrayOf(Storage("completion.time.statistics")))
class ContributorsTimeStatistics : PersistentStateComponent<CompletionTimeStats> {

    private var statsState: CompletionTimeStats = CompletionTimeStats()

    override fun loadState(state: CompletionTimeStats) {
        statsState = state
    }

    override fun getState(): CompletionTimeStats {
        return statsState
    }

    companion object {
        fun registerCompletionContributorsTime(languge: Language, timeTaken: Long) {
            val stats = getInstance().statsState
            stats.registerCompletionContributorsTime(languge, timeTaken)
        }

        fun registerSecondCompletionContributorsTime(languge: Language, timeTaken: Long) {
            val stats = getInstance().statsState
            stats.registerSecondCompletionTime(languge, timeTaken)
        }

        fun getInstance() = service<ContributorsTimeStatistics>()
    }

}

class CompletionTimeStats {
    companion object {
        private val MIN_POWER = 7
        private val MAX_POWER = 17
        private val EXPONENT = 2.0
    }

    @JvmField val completionIntervals = HashMap<Language, IntervalCounter>()
    @JvmField val secondCompletionIntervals = HashMap<Language, IntervalCounter>()

    fun intervals(languge: Language) = completionIntervals[languge]
    fun secondCompletionIntervals(languge: Language) = secondCompletionIntervals[languge]

    fun registerCompletionContributorsTime(language: Language, timeTaken: Long) {
        val counter = completionIntervals[language] ?: IntervalCounter(MIN_POWER, MAX_POWER, EXPONENT)
        counter.register(timeTaken)
        completionIntervals[language] = counter
    }

    fun registerSecondCompletionTime(language: Language, timeTaken: Long) {
        val counter = secondCompletionIntervals[language] ?: IntervalCounter(MIN_POWER, MAX_POWER, EXPONENT)
        counter.register(timeTaken)
        secondCompletionIntervals[language] = counter
    }

    fun languages() = (completionIntervals.keys + secondCompletionIntervals.keys).toList()

}
