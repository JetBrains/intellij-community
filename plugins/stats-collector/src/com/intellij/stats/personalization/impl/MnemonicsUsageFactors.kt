package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.*

/**
 * @author Vitaliy.Bibaev
 */
class MnemonicsUsageReader(factor: DailyAggregatedDoubleFactor) : UserFactorReaderBase(factor) {
    fun mnemonicsUsageRatio(): Double? {
        val sums = factor.aggregateSum()
        val total = sums["total"]
        val used = sums["withMnemonics"]
        if (total == null || used == null || total < 1.0) return null
        return used / total
    }
}

class MnemonicsUsageUpdater(factor: MutableDoubleFactor) : UserFactorUpdaterBase(factor) {
    fun fireCompletionFinished(isMnemonicsUsed: Boolean) {
        factor.updateOnDate(DateUtil.today()) {
            compute("total", { _, before -> if (before == null) 1.0 else before + 1 })
            val valueBefore = computeIfAbsent("withMnemonics", { 0.0 })
            if (isMnemonicsUsed) {
                set("withMnemonics", valueBefore + 1.0)
            }
        }
    }
}

class MnemonicsRatio : UserFactorBase<MnemonicsUsageReader>("mnemonicsUsageRatio", UserFactorDescriptions.MNEMONICS_USAGE) {
    override fun compute(reader: MnemonicsUsageReader): String? = reader.mnemonicsUsageRatio()?.toString()
}