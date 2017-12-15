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

class CompletionFinishTypeReader(private val factor: DailyAggregatedDoubleFactor) : FactorReader {
    fun getTotalExplicitSelectCount(): Double =
            factor.aggregateSum()[explicitSelectKey] ?: 0.0

    fun getTotalTypedSelectCount(): Double =
            factor.aggregateSum()[typedSelectKey] ?: 0.0

}

class CompletionFinishTypeUpdater(private val factor: MutableDoubleFactor) : FactorUpdater {
    fun fireExplicitCompletionPerformed() {
        factor.incrementOnToday(explicitSelectKey)
    }

    fun fireTypedSelectPerformed() {
        factor.incrementOnToday(typedSelectKey)
    }
}

class ExplicitCompletionRatio : UserFactor {
    override val id: String = "explicitSelectRatio"

    override fun compute(storage: UserFactorStorage): String? {
        val factorReader = storage.getFactorReader(UserFactorDescriptions.COMPLETION_FINISH_TYPE)
        val total = factorReader.getTotalExplicitSelectCount() + factorReader.getTotalTypedSelectCount()
        if (total == 0.0) return null
        return (factorReader.getTotalExplicitSelectCount() / total).toString()
    }
}