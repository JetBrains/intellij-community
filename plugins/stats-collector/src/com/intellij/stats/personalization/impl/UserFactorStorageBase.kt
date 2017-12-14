package com.intellij.stats.personalization.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.stats.personalization.*
import com.intellij.util.attribute
import org.jdom.Element
import java.util.*

abstract class UserFactorStorageBase
    : UserFactorStorage, PersistentStateComponent<Element> {

    private val state = CollectorState()

    override fun <U : FactorUpdater> getFactorUpdater(description: UserFactorDescription<U, *>): U =
            description.updaterFactory.invoke(getAggregateFactor(description.factorId))

    override fun <R : FactorReader> getFactorReader(description: UserFactorDescription<*, R>): R =
            description.readerFactory.invoke(getAggregateFactor(description.factorId))

    override fun getState(): Element {
        val element = Element("component")
        val start = System.currentTimeMillis()
        state.writeState(element)
        val end = System.currentTimeMillis()
        println("saving of user factors took it in ${end - start}ms")
        return element
    }

    override fun loadState(newState: Element) {
        state.applyState(newState)
    }

    private fun getAggregateFactor(factorId: String): MutableDoubleFactor =
            state.aggregateFactors.computeIfAbsent(factorId, { DailyAggregateFactor() })

    private class CollectorState {
        val aggregateFactors: MutableMap<String, DailyAggregateFactor> = HashMap()

        fun applyState(element: Element) {
            aggregateFactors.clear()
            if (element.name == "userFactors") {
                for (child in element.children) {
                    val factorId = child.getAttributeValue("id")
                    if (child.name == "factor" && factorId != null) {
                        val factor = DailyAggregateFactor.restore(child)
                        if (factor != null) aggregateFactors[factorId] = factor
                    }
                }
            }
        }

        fun writeState(element: Element) {
            for ((id, factor) in aggregateFactors) {
                val factorElement = Element("factor")
                factorElement.attribute("id", id)
                factor.writeState(factorElement)
                element.addContent(factorElement)
            }
        }
    }

    class DailyAggregateFactor private constructor(private val aggregates: SortedMap<Day, DailyData>) : MutableDoubleFactor {
        constructor() : this(sortedMapOf())

        companion object {
            fun restore(element: Element): DailyAggregateFactor? {
                val data = sortedMapOf<Day, DailyData>()
                for (child in element.children) {
                    val date = child.getAttributeValue("date")
                    val day = DayImpl.fromString(date)
                    if (child.name == "dailyData" && day != null) {
                        val dailyData = DailyData.restore(child)
                        if (dailyData != null) data.put(day, dailyData)
                    }
                }

                if (data.isEmpty()) return null
                return DailyAggregateFactor(data)
            }
        }

        fun writeState(element: Element) {
            for ((day, data) in aggregates) {
                val dailyDataElement = Element("dailyData")
                dailyDataElement.attribute("date", day.toString())
                data.writeState(dailyDataElement)
                element.addContent(dailyDataElement)
            }
        }

        override fun availableDays(): List<Day> = aggregates.keys.toList()

        override fun incrementOnToday(key: String) {
            aggregates.onToday().compute(key, { _, oldValue -> if (oldValue == null) 1.0 else oldValue + 1.0 })
        }

        override fun onDate(date: Day): Map<String, Double>? = aggregates[date]?.data

        override fun setOnDate(date: Day, key: String, value: Double) = aggregates.onDate(date).set(key, value)

        override fun updateOnDate(date: Day, updater: MutableMap<String, Double>.() -> Unit) {
            aggregates.compute(date) { _, data ->
                if (data == null) {
                    val dailyData = DailyData()
                    updater.invoke(dailyData.data)
                    dailyData
                } else {
                    updater.invoke(data.data)
                    data
                }
            }
        }

        private fun SortedMap<Day, DailyData>.onDate(date: Day): MutableMap<String, Double> =
                this.computeIfAbsent(date, { DailyData() }).data

        private fun SortedMap<Day, DailyData>.onToday(): MutableMap<String, Double> =
                this.onDate(DateUtil.today())
    }

    private class DailyData(val data: MutableMap<String, Double> = HashMap()) {
        companion object {
            fun restore(element: Element): DailyData? {
                val data = mutableMapOf<String, Double>()
                for (child in element.children) {
                    if (child.name == "observation") {
                        val dataKey = child.getAttributeValue("name")
                        val dataValue = child.getAttributeValue("value")

                        // skip all if any observation is inconsistent
                        val value = dataValue.toDoubleOrNull() ?: return null
                        data[dataKey] = value
                    }
                }

                if (data.isEmpty()) return null
                return DailyData(data)
            }
        }

        fun writeState(element: Element) {
            for ((key, value) in data) {
                val observation = Element("observation")
                observation.attribute("name", key)
                observation.attribute("value", value.toString())
                element.addContent(observation)
            }
        }
    }
}