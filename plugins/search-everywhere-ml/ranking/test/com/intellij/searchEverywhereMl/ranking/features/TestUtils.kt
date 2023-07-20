package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair

@Suppress("UNCHECKED_CAST")
internal inline fun <reified T> List<EventPair<*>>.findFeature(field: EventField<T>): EventPair<T>? = find {
  it.field.name == field.name
} as? EventPair<T>

internal inline fun <reified T> List<EventPair<*>>.findFeatureValue(field: EventField<T>): T? = findFeature(field)?.data

internal inline fun <reified T> List<EventPair<*>>.getFeatureValue(field: EventField<T>): T = findFeatureValue(field)!!
