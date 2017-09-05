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


class UpPressedEvent(
        userId: String, 
        sessionId: String,
        completionListIds: List<Int>, 
        newCompletionListItems: List<LookupEntryInfo>, 
        selectedPosition: Int) : LookupStateLogData(userId, sessionId, Action.UP, completionListIds, newCompletionListItems, selectedPosition) {

    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
}

class DownPressedEvent(
        userId: String,
        sessionId: String,
        completionListIds: List<Int>,
        newCompletionListItems: List<LookupEntryInfo>,
        selectedPosition: Int) : LookupStateLogData(userId, sessionId, Action.DOWN, completionListIds, newCompletionListItems, selectedPosition) {

    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
    
}

class CompletionCancelledEvent(userId: String, sessionId: String) : LogEvent(userId, sessionId, Action.COMPLETION_CANCELED) {
    
    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
    
}

/**
 * selectedId here, because position is 0 here
 */
class TypedSelectEvent(userId: String, sessionId: String, @JvmField var selectedId: Int) : LogEvent(userId, sessionId, Action.TYPED_SELECT) {
    
    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
    
}

class ExplicitSelectEvent(userId: String, 
                          sessionId: String,
                          completionListIds: List<Int>,
                          newCompletionListItems: List<LookupEntryInfo>,
                          selectedPosition: Int,
                          @JvmField var selectedId: Int) : LookupStateLogData(userId, sessionId, Action.EXPLICIT_SELECT, completionListIds, newCompletionListItems, selectedPosition) {
    
    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
    
}

class BackspaceEvent(
        userId: String,
        sessionId: String,
        completionListIds: List<Int>,
        newCompletionListItems: List<LookupEntryInfo>, 
        selectedPosition: Int) : LookupStateLogData(userId, sessionId, Action.BACKSPACE, completionListIds, newCompletionListItems, selectedPosition) {
    
    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
}

class TypeEvent(
        userId: String,
        sessionId: String,
        completionListIds: List<Int>,
        newCompletionListItems: List<LookupEntryInfo>,
        selectedPosition: Int) : LookupStateLogData(userId, sessionId, Action.TYPE, completionListIds, newCompletionListItems, selectedPosition) {
    
    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
    
    fun newCompletionIds(): List<Int> = newCompletionListItems.map { it.id } 
}

class CompletionStartedEvent(
  @JvmField var ideVersion: String,
  @JvmField var pluginVersion: String,
  @JvmField var mlRankingVersion: String,
  userId: String,
  sessionId: String,
  @JvmField var language: String?,
  @JvmField var performExperiment: Boolean,
  @JvmField var experimentVersion: Int,
  completionList: List<LookupEntryInfo>,
  selectedPosition: Int)
    
    : LookupStateLogData(
        userId, 
        sessionId, 
        Action.COMPLETION_STARTED, 
        completionList.map { it.id }, 
        completionList, 
        selectedPosition)
{

    //seems it's not needed, remove when possible
    @JvmField var completionListLength: Int = completionList.size
    @JvmField var isOneLineMode: Boolean = false

    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
}

class CustomMessageEvent(userId: String, sessionId: String, @JvmField var text: String): LogEvent(userId, sessionId, Action.CUSTOM) {
    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
}

class LookupEntryInfo(val id: Int, val length: Int, val relevance: Map<String, String?>?)


abstract class LogEventVisitor {
    open fun visit(event: CompletionStartedEvent) {}
    open fun visit(event: TypeEvent) {}
    open fun visit(event: DownPressedEvent) {}
    open fun visit(event: UpPressedEvent) {}
    open fun visit(event: BackspaceEvent) {}
    open fun visit(event: CompletionCancelledEvent) {}
    open fun visit(event: ExplicitSelectEvent) {}
    open fun visit(event: TypedSelectEvent) {}
    open fun visit(event: CustomMessageEvent) {}
}


enum class Action {
    COMPLETION_STARTED,
    TYPE,
    BACKSPACE,
    UP,
    DOWN,
    COMPLETION_CANCELED,
    EXPLICIT_SELECT,
    TYPED_SELECT,
    CUSTOM
}