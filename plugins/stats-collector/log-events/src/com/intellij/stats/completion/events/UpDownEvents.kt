// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.stats.completion.events

import com.intellij.stats.completion.Action
import com.intellij.stats.completion.LogEventVisitor
import com.intellij.stats.completion.LookupEntryInfo


class UpPressedEvent(
        userId: String,
        sessionId: String,
        completionListIds: List<Int>,
        newCompletionListItems: List<LookupEntryInfo>,
        selectedPosition: Int,
        timestamp: Long)
    : LookupStateLogData(userId, sessionId, Action.UP, completionListIds,
        newCompletionListItems, selectedPosition, timestamp) {

    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
}


class DownPressedEvent(
        userId: String,
        sessionId: String,
        completionListIds: List<Int>,
        newCompletionListItems: List<LookupEntryInfo>,
        selectedPosition: Int,
        timestamp: Long)
    : LookupStateLogData(userId, sessionId, Action.DOWN, completionListIds,
        newCompletionListItems, selectedPosition, timestamp) {

    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }

}