// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.tracker

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.stats.completion.Action.*
import com.intellij.stats.completion.events.ExplicitSelectEvent
import com.intellij.stats.completion.events.LogEvent
import com.intellij.stats.completion.events.TypedSelectEvent
import org.assertj.core.api.Assertions.assertThat


class CompletionEventsLoggingTest : CompletionLoggingTestBase() {

    fun `test item selected on just typing`() {
        myFixture.type('.')
        myFixture.completeBasic()
        val itemsOnStart = lookup.items
        myFixture.type("ru")

        trackedEvents.assertOrder(
          COMPLETION_STARTED,
          TYPE
        )

        myFixture.type("n)")

        trackedEvents.assertOrder(
          COMPLETION_STARTED,
          TYPE,
          TYPE,
          TYPE,
          TYPED_SELECT
        )

        checkLoggedAllElements(itemsOnStart)
        checkSelectedCorrectId(itemsOnStart, "run")
    }

    private fun checkLoggedAllElements(itemsOnStart: MutableList<LookupElement>) {
        assertThat(completionStartedEvent.newCompletionListItems).hasSize(itemsOnStart.size)
        assertThat(completionStartedEvent.completionListIds).hasSize(itemsOnStart.size)
    }

    private fun checkSelectedCorrectId(itemsOnStart: MutableList<LookupElement>, selectedString: String) {
        val selectedIndex = itemsOnStart.indexOfFirst { it.lookupString == selectedString }
        val selectedId = completionStartedEvent.completionListIds[selectedIndex]
        assertThat(trackedEvents.last().extractSelectedId()).isEqualTo(selectedId)
    }

    private fun LogEvent.extractSelectedId(): Int? {
        return when (this) {
            is ExplicitSelectEvent -> selectedId
            is TypedSelectEvent -> selectedId
            else -> null
        }
    }

    fun `test wrong typing`() {
        myFixture.type('.')
        myFixture.completeBasic()

        myFixture.type('r')
        myFixture.type('u')
        myFixture.type('x')

        lookup.hide() //figure out why needed here

        trackedEvents.assertOrder(
          COMPLETION_STARTED,
          TYPE,
          TYPE,
          TYPE,
          COMPLETION_CANCELED
        )
    }
    
    fun `test down up buttons`() {
        myFixture.type('.')
        myFixture.completeBasic()
        val elementsOnStart = lookup.items

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)

        myFixture.type('\n')


        trackedEvents.assertOrder(
          COMPLETION_STARTED,
          DOWN,
          DOWN,
          DOWN,
          UP,
          UP,
          UP,
          EXPLICIT_SELECT
        )

        checkLoggedAllElements(elementsOnStart)
        checkSelectedCorrectId(elementsOnStart, elementsOnStart.first().lookupString)
    }


    fun `test backspace`() {
        myFixture.type('.')
        myFixture.completeBasic()
        val elementsOnStart = lookup.items

        myFixture.type("ru")
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
        myFixture.type('u')
        myFixture.type('\n')

        trackedEvents.assertOrder(
          COMPLETION_STARTED,
          TYPE,
          TYPE,
          BACKSPACE,
          TYPE,
          EXPLICIT_SELECT
        )

        checkSelectedCorrectId(elementsOnStart, "run")
        checkLoggedAllElements(elementsOnStart)
    }

    fun `test if typed prefix is correct completion variant, pressing dot will select it`() {
        myFixture.completeBasic()
        val elementsOnStart = lookup.items
        myFixture.type('.')

        trackedEvents.assertOrder(
          COMPLETION_STARTED,
          TYPED_SELECT
        )

        checkSelectedCorrectId(elementsOnStart, "r")
        checkLoggedAllElements(elementsOnStart)
    }

    fun `test dot selection logs as explicit select`() {
        myFixture.completeBasic()
        val elementsOnStart = lookup.items
        myFixture.type('u')
        myFixture.type('.')

        trackedEvents.assertOrder(
          COMPLETION_STARTED,
          TYPE,
          EXPLICIT_SELECT
        )

        checkSelectedCorrectId(elementsOnStart, "run")
        checkLoggedAllElements(elementsOnStart)
    }
}