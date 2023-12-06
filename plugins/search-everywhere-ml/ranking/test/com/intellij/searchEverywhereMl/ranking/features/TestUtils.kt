@file:Suppress("UNCHECKED_CAST")

package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.internal.statistic.eventLog.events.*
import org.junit.Assert

internal inline fun <reified T> List<EventPair<*>>.findFeature(field: EventField<T>): EventPair<T>? = find {
  it.field.name == field.name
} as? EventPair<T>

internal inline fun <reified T> List<EventPair<*>>.findFeatureValue(field: EventField<T>): T? = findFeature(field)?.data

internal inline fun <reified T> List<EventPair<*>>.getFeatureValue(field: EventField<T>): T = findFeatureValue(field)!!

internal inline operator fun <reified T> Map<String, Any>.get(key: PrimitiveEventField<T>) = this[key.name] as T

internal operator fun Map<String, Any>.get(key: ObjectListEventField): List<Map<String, Any>> = this[key.name] as List<Map<String, Any>>

internal operator fun Map<String, Any>.get(key: ObjectEventField): Map<String, Any> = this[key.name] as Map<String, Any>

internal inline fun <reified T> Map<String, Any>.assert(key: PrimitiveEventField<T>, expected: T) = also { Assert.assertEquals(expected, this[key.name] as T) }
