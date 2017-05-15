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

object JsonSerializer {
    private val gson = Gson()
    fun toJson(obj: Any) = gson.toJson(obj)
    fun <T> fromJson(json: String, clazz: Class<T>) = gson.fromJson(json, clazz)
}


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

    fun fromString(line: String): LogEvent? {
        val items = mutableListOf<String>()

        var start = -1
        for (i in 0..4) {
            val nextSpace = line.indexOf('\t', start + 1)
            val newItem = line.substring(start + 1, nextSpace)
            items.add(newItem)
            start = nextSpace
        }

        val timestamp = items[0].toLong()
        val recorderId = items[1]
        val userUid = items[2]
        val sessionUid = items[3]
        val actionType = Action.valueOf(items[4])

        val clazz = actionClassMap[actionType] ?: return null

        val json = line.substring(start + 1)
        val obj = JsonSerializer.fromJson(json, clazz)

        obj.userUid = userUid
        obj.timestamp = timestamp
        obj.recorderId = recorderId
        obj.sessionUid = sessionUid
        obj.actionType = actionType

        return obj
    }

}