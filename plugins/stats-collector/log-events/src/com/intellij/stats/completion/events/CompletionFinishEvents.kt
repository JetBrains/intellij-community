// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.stats.completion.events

import com.intellij.stats.completion.Action
import com.intellij.stats.completion.ElementPositionHistory
import com.intellij.stats.completion.LogEventVisitor
import com.intellij.stats.completion.LookupEntryInfo


class CompletionCancelledEvent(userId: String, sessionId: String, timestamp: Long)
    : LogEvent(userId, sessionId, Action.COMPLETION_CANCELED, timestamp) {
    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
}


class ExplicitSelectEvent(
        userId: String,
        sessionId: String,
        newCompletionListItems: List<LookupEntryInfo>,
        selectedPosition: Int,
        @JvmField var selectedId: Int,
        @JvmField var completionList: List<LookupEntryInfo>,
        @JvmField var history: Map<Int, ElementPositionHistory>,
        timestamp: Long
) : LookupStateLogData(
        userId,
        sessionId,
        Action.EXPLICIT_SELECT,
        completionList.map { it.id },
        newCompletionListItems,
        selectedPosition,
        timestamp
) {

    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }

}


/**
 * selectedId here, because position is 0 here
 */
class TypedSelectEvent(
        userId: String,
        sessionId: String,
        newCompletionListItems: List<LookupEntryInfo>,
        @JvmField var selectedId: Int,
        @JvmField var completionList: List<LookupEntryInfo>,
        @JvmField var history: Map<Int, ElementPositionHistory>,
        timestamp: Long
) : LookupStateLogData(userId, sessionId, Action.TYPED_SELECT, newCompletionListItems.map { it.id },
        newCompletionListItems, 0, timestamp) {

    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }

}