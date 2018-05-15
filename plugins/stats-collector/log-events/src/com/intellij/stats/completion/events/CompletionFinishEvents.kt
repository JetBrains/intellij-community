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

package com.intellij.stats.completion.events

import com.intellij.stats.completion.Action
import com.intellij.stats.completion.ElementPositionHistory
import com.intellij.stats.completion.LogEventVisitor
import com.intellij.stats.completion.LookupEntryInfo


class CompletionCancelledEvent(userId: String, sessionId: String) : LogEvent(userId, sessionId, Action.COMPLETION_CANCELED) {
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
        @JvmField var history: Map<Int, ElementPositionHistory>
)
    : LookupStateLogData(
        userId,
        sessionId,
        Action.EXPLICIT_SELECT,
        completionList.map { it.id },
        newCompletionListItems,
        selectedPosition
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
        @JvmField var history: Map<Int, ElementPositionHistory>
)

    : LookupStateLogData(userId, sessionId, Action.TYPED_SELECT, newCompletionListItems.map { it.id }, newCompletionListItems, 0) {

    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }

}