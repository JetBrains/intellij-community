package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.UserFactorBase
import com.intellij.stats.personalization.UserFactorDescriptions
import com.intellij.stats.personalization.UserFactorReaderBase
import com.intellij.stats.personalization.UserFactorUpdaterBase

/**
 * @author Vitaliy.Bibaev
 */
class ItemPositionReader(factor: DailyAggregatedDoubleFactor) : UserFactorReaderBase(factor) {
    fun getCountsByPosition(): Map<Int, Double> {
        return factor.aggregateSum().asIterable().associate { (key, value) -> key.toInt() to value }
    }

    fun getAveragePosition(): Double? {
        val positionToCount = getCountsByPosition()
        if (positionToCount.isEmpty()) return null

        val positionsSum = positionToCount.asSequence().sumByDouble { it.key * it.value }
        val completionCount = positionToCount.asSequence().sumByDouble { it.value }

        if (completionCount == 0.0) return null
        return positionsSum / completionCount
    }
}

class ItemPositionUpdater(factor: MutableDoubleFactor) : UserFactorUpdaterBase(factor) {
    fun fireCompletionPerformed(selectedItemOrder: Int) {
        factor.incrementOnToday(selectedItemOrder.toString())
    }
}

class AverageSelectedItemPosition()
    : UserFactorBase<ItemPositionReader>("averageSelectedPosition", UserFactorDescriptions.SELECTED_ITEM_POSITION) {
    override fun compute(reader: ItemPositionReader): String? = reader.getAveragePosition()?.toString()
}

class MaxSelectedItemPosition()
    : UserFactorBase<ItemPositionReader>("maxSelectedItemPosition", UserFactorDescriptions.SELECTED_ITEM_POSITION) {
    override fun compute(reader: ItemPositionReader): String? =
            reader.getCountsByPosition().asSequence().filter { it.value != 0.0 }.maxBy { it.key }?.key?.toString()
}

class MostFrequentSelectedItemPosition()
    : UserFactorBase<ItemPositionReader>("mostFrequentItemPosition", UserFactorDescriptions.SELECTED_ITEM_POSITION) {
    override fun compute(reader: ItemPositionReader): String? =
            reader.getCountsByPosition().maxBy { it.value }?.key?.toString()
}

