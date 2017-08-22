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
package com.intellij.completion.tracker

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.PrefixChangeListener
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key


data class StagePosition(val stage: Int, val position: Int)


interface LookupElementTracking {
    fun positionsHistory(element: LookupElement): List<StagePosition>

    companion object {
        fun getInstance(): LookupElementTracking = service()
    }
}


private class UserDataLookupElementTracking : LookupElementTracking {

    override fun positionsHistory(element: LookupElement): List<StagePosition> {
        element.putUserDataIfAbsent(KEY, mutableListOf())
        return element.getUserData(KEY)!!
    }

    companion object {
        private val KEY = Key.create<MutableList<StagePosition>>("lookup.element.position.history")

        fun addElementPosition(element: LookupElement, stagePosition: StagePosition) {
            element.putUserDataIfAbsent(KEY, mutableListOf())
            val positionHistory = element.getUserData(KEY)!!
            positionHistory.add(stagePosition)
        }
    }

}


class ShownTimesTrackerInitializer : ApplicationComponent {

    private val lookupLifecycleListener = object : LookupLifecycleListener {
        override fun lookupCreated(lookup: LookupImpl) {
            val shownTimesTracker = ShownTimesTrackingListener(lookup)
            lookup.setPrefixChangeListener(shownTimesTracker)
        }
    }

    override fun initComponent() {
        val listener = lookupLifecycleListenerInitializer(lookupLifecycleListener)
        registerProjectManagerListener(listener)
    }

}


private class ShownTimesTrackingListener(private val lookup: LookupImpl): PrefixChangeListener {
    private var stage = 0

    override fun beforeAppend(c: Char) = update()
    override fun beforeTruncate() = update()

    private fun update() {
        lookup.items.forEachIndexed { index, lookupElement ->
            UserDataLookupElementTracking.addElementPosition(lookupElement, StagePosition(stage, index))
        }
        stage++
    }
}