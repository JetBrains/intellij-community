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
package com.intellij.stats.events.completion

import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import java.lang.reflect.Field

object JsonSerializer {
    private val gson = Gson()
    private val ignoredFields = setOf(
      "recorderId", "timestamp", "sessionUid", "actionType", "userUid"
    )

    fun toJson(obj: Any): String = gson.toJson(obj)

    fun <T> fromJson(json: String, clazz: Class<T>): DeserializationResult<T> {
        val declaredFields = allFields(clazz).map { it.name }.filter { it !in ignoredFields }
        val jsonFields = gson.fromJson(json, LinkedTreeMap::class.java).keys.map { it.toString() }.toSet()
        val value = gson.fromJson(json, clazz)

        val unknownFields = jsonFields.subtract(declaredFields)
        val absentFields = declaredFields.subtract(jsonFields)

        return DeserializationResult(value, unknownFields, absentFields)
    }

    private fun <T> allFields(clazz: Class<T>): List<Field> {
        val fields: List<Field> = clazz.declaredFields.toList()
        if (clazz.superclass != null) {
            return fields + allFields(clazz.superclass)
        }
        return fields
    }
}


class DeserializationResult<out T>(val value: T, val unknownFields: Set<String>, val absentFields: Set<String>)


object LogEventSerializer {


    private val actionClassMap: Map<Action, Class<out LogEvent>> = mapOf(
        Action.COMPLETION_STARTED to CompletionStartedEvent::class.java,
        Action.TYPE to TypeEvent::class.java,
        Action.DOWN to DownPressedEvent::class.java,
        Action.UP to UpPressedEvent::class.java,
        Action.BACKSPACE to BackspaceEvent::class.java,
        Action.COMPLETION_CANCELED to CompletionCancelledEvent::class.java,
        Action.EXPLICIT_SELECT to ExplicitSelectEvent::class.java,
        Action.TYPED_SELECT to TypedSelectEvent::class.java,
        Action.CUSTOM to CustomMessageEvent::class.java
    )


    fun toString(event: LogEvent): String {
        return "${event.timestamp}\t${event.recorderId}\t${event.userUid}\t${event.sessionUid}\t${event.actionType}\t${JsonSerializer.toJson(event)}"
    }


    fun fromString(line: String): DeserializedLogEvent {
        val pair = tabSeparatedValues(line) ?: return DeserializedLogEvent(null, emptySet(), emptySet())
        val items = pair.first
        val start = pair.second

        val timestamp = items[0].toLong()
        val recorderId = items[1]
        val userUid = items[2]
        val sessionUid = items[3]
        val actionType = Action.valueOf(items[4])

        val clazz = actionClassMap[actionType] ?: return DeserializedLogEvent(null, emptySet(), emptySet())

        val json = line.substring(start + 1)
        val result = JsonSerializer.fromJson(json, clazz)

        val event = result.value

        event.userUid = userUid
        event.timestamp = timestamp
        event.recorderId = recorderId
        event.sessionUid = sessionUid
        event.actionType = actionType

        return DeserializedLogEvent(event, result.unknownFields, result.absentFields)
    }

    private fun tabSeparatedValues(line: String): Pair<List<String>, Int>? {
        val items = mutableListOf<String>()
        var start = -1
        try {
            for (i in 0..4) {
                val nextSpace = line.indexOf('\t', start + 1)
                val newItem = line.substring(start + 1, nextSpace)
                items.add(newItem)
                start = nextSpace
            }
            return Pair(items, start)
        }
        catch (e: Exception) {
            return null
        }
    }

}


class DeserializedLogEvent(
  val event: LogEvent?,
  val unknownEventFields: Set<String>,
  val absentEventFields: Set<String>
) {

    val isFailed: Boolean
      get() = unknownEventFields.isNotEmpty() || absentEventFields.isNotEmpty()

}