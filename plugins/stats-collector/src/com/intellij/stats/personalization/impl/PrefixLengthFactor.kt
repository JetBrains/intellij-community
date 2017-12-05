package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.*

/**
 * @author Vitaliy.Bibaev
 */
class PrefixLengthReader(factor: DailyAggregatedDoubleFactor) : UserFactorReaderBase(factor) {
    fun getCountsByPrefixLength(): Map<Int, Double> {
        return factor.aggregateSum().asIterable().associate { (key, value) -> key.toInt() to value }
    }

    fun getAveragePrefixLength(): Double? {
        val lengthToCount = getCountsByPrefixLength()
        if (lengthToCount.isEmpty()) return null

        val totalChars = lengthToCount.asSequence().sumByDouble { it.key * it.value }
        val completionCount = lengthToCount.asSequence().sumByDouble { it.value }

        if (completionCount == 0.0) return null
        return totalChars / completionCount
    }
}

class PrefixLengthUpdater(factor: MutableDoubleFactor) : UserFactorUpdaterBase(factor) {
    fun fireCompletionPerformed(prefixLength: Int) {
        factor.incrementOnToday(prefixLength.toString())
    }
}

class MostFrequentPrefixLength : UserFactorBase<PrefixLengthReader>("mostFrequentPrefixLength",
        UserFactorDescriptions.PREFIX_LENGTH_ON_COMPLETION) {
    override fun compute(reader: PrefixLengthReader): String? {
        return reader.getCountsByPrefixLength().maxBy { it.value }?.key?.toString()
    }
}

class AveragePrefixLength : UserFactorBase<PrefixLengthReader>("", UserFactorDescriptions.PREFIX_LENGTH_ON_COMPLETION) {
    override fun compute(reader: PrefixLengthReader): String? {
        return reader.getAveragePrefixLength()?.toString()
    }
}