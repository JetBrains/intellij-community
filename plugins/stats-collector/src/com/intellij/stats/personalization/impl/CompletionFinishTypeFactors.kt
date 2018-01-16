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

package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.*

/**
 * @author Vitaliy.Bibaev
 */
private val explicitSelectKey = "explicitSelect"
private val typedSelectKey = "typedSelect"
private val cancelledKey = "cancelled"

class CompletionFinishTypeReader(private val factor: DailyAggregatedDoubleFactor) : FactorReader {
    fun getCountByKey(key: String): Double = factor.aggregateSum()[key] ?: 0.0

    fun getTotalCount(): Double =
            getCountByKey(explicitSelectKey) + getCountByKey(typedSelectKey) + getCountByKey(cancelledKey)
}

class CompletionFinishTypeUpdater(private val factor: MutableDoubleFactor) : FactorUpdater {
    fun fireExplicitCompletionPerformed() = factor.incrementOnToday(explicitSelectKey)
    fun fireTypedSelectPerformed() = factor.incrementOnToday(typedSelectKey)
    fun fireLookupCancelled() = factor.incrementOnToday(cancelledKey)

}

sealed class CompletionFinishTypeRatioBase(private val key: String)
    : UserFactorBase<CompletionFinishTypeReader>("completionFinishType${key.capitalize()}", UserFactorDescriptions.COMPLETION_FINISH_TYPE) {
    override fun compute(reader: CompletionFinishTypeReader): String? {
        val total = reader.getTotalCount()
        if (total <= 0) return null
        return (reader.getCountByKey(key) / total).toString()
    }
}

class ExplicitSelectRatio : CompletionFinishTypeRatioBase(explicitSelectKey)
class TypedSelectRatio : CompletionFinishTypeRatioBase(typedSelectKey)
class LookupCancelledRatio : CompletionFinishTypeRatioBase(cancelledKey)

