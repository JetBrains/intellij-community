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

package com.intellij.sorting

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.containers.ContainerUtil


@State(name = "SortingTimeStatistics", storages=arrayOf(Storage("ml.sorting.time.stats.xml")))
class SortingTimeStatistics: PersistentStateComponent<TimingStatState> {

    private var statsState: TimingStatState = TimingStatState()

    override fun loadState(state: TimingStatState) {
        statsState = state
    }

    override fun getState(): TimingStatState {
        return statsState
    }
    
    companion object {
        fun registerSortTiming(elementsSorted: Int, timeSpentMillis: Long) {
            val stats = getInstance().statsState
            stats.registerSortTiming(elementsSorted, timeSpentMillis)
        }
        
        fun getInstance(): SortingTimeStatistics = ServiceManager.getService(SortingTimeStatistics::class.java)
    }
    
}


class TimingStatState {
    companion object {
        private val size = 10
    }
    
    
    @JvmField val totalSortsByTime = MutableList(size, { 0 })
    @JvmField val avgSortingTimeByN = MutableList(size, { 0.0 })
    @JvmField val totalSortsByN = MutableList(size, { 0 })
    

    fun registerSortTiming(elementsSorted: Int, timeSpentMillis: Long) {
        val elementIndex = Math.min(elementsSorted / 100, size - 1)
        
        val total = totalSortsByN[elementIndex]
        val newArg = (avgSortingTimeByN[elementIndex] * total + timeSpentMillis) / (total + 1)
        
        totalSortsByN[elementIndex] += 1
        avgSortingTimeByN[elementIndex] = newArg
        
        val timeIndex = Math.min(timeSpentMillis.toInt() / 100, size - 1)
        totalSortsByTime[timeIndex] += 1
    }
    
    
    fun getTimeDistribution(): List<String> {
        return totalSortsByTime.mapIndexedNotNull({ index, total ->
            if (total == 0) return@mapIndexedNotNull null 
            val start = index * 100
            val end = (start + 100).toString() + if (index == size - 1) "+" else ""
            "[$start, $end) ms sorting happened $total times"
        })
    }

    
    fun getAvgTimeByElementsSortedDistribution(): List<String> {
        return avgSortingTimeByN.mapIndexedNotNull { index, avg -> 
            if (avg < 0.01) return@mapIndexedNotNull null
            val start = index * 100
            val end = (start + 100).toString() + if (index == size - 1) "+" else ""
            "[$start, $end) elements sorted on average in ${avg.format(1)} ms"
        }
    }

    
}

fun Double.format(digits: Int): String = java.lang.String.format("%.${digits}f", this)
