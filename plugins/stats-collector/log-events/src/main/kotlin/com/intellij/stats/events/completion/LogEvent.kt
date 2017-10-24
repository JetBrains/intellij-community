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


abstract class LogEvent(@Transient var userUid: String, sessionId: String, type: Action) {
    @Transient var recorderId = "completion-stats"
    @Transient var timestamp = System.currentTimeMillis()
    @Transient var sessionUid: String = sessionId
    @Transient var actionType: Action = type

    abstract fun accept(visitor: LogEventVisitor)
}


abstract class LookupStateLogData(
        userId: String,
        sessionId: String,
        action: Action,
        @JvmField var completionListIds: List<Int>,
        @JvmField var newCompletionListItems: List<LookupEntryInfo>,
        @JvmField var currentPosition: Int
) : LogEvent(userId, sessionId, action)