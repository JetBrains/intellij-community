// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.console

import com.intellij.openapi.util.TextRange

class CommandHistory {
    class Entry(
        val entryText: String,
        val rangeInHistoryDocument: TextRange
    )

    private val entries = arrayListOf<Entry>()
    var processedEntriesCount: Int = 0
        private set

    val listeners = arrayListOf<HistoryUpdateListener>()

    operator fun get(i: Int) = entries[i]

    fun addEntry(entry: Entry) {
        entries.add(entry)
        listeners.forEach { it.onNewEntry(entry) }
    }

    fun lastUnprocessedEntry(): Entry? = if (processedEntriesCount < size) {
        get(processedEntriesCount)
    } else {
        null
    }

    fun entryProcessed() {
        processedEntriesCount++
    }

    val size: Int get() = entries.size
}

interface HistoryUpdateListener {
    fun onNewEntry(entry: CommandHistory.Entry)
}