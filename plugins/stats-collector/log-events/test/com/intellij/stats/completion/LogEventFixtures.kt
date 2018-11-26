// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.stats.completion

import com.intellij.stats.completion.events.*

@Suppress("unused")
object LogEventFixtures {

    const val sessionId = "session-id-xxx"

    val completion_started_3_items_shown = CompletionStartedEvent("", "", "", "1", sessionId,
                                                                  "Java", true, 1, Fixtures.lookupList,
                                                                  Fixtures.userFactors, 0, 0, System.currentTimeMillis())

    val completion_cancelled = CompletionCancelledEvent("1", sessionId, System.currentTimeMillis())

    val type_event_current_pos_0_left_ids_1_2 = TypeEvent("1", sessionId, listOf(1, 2), emptyList(), 0, 1, System.currentTimeMillis())
    val type_event_current_pos_0_left_ids_0_1 = TypeEvent("1", sessionId, listOf(0, 1), emptyList(), 0, 1, System.currentTimeMillis())
    val type_event_current_pos_0_left_id_0 = TypeEvent("1", sessionId, listOf(0), emptyList(), 0, 1, System.currentTimeMillis())

    val up_pressed_new_pos_0 = UpPressedEvent("1", sessionId, emptyList(), emptyList(), 0, System.currentTimeMillis())
    val up_pressed_new_pos_1 = UpPressedEvent("1", sessionId, emptyList(), emptyList(), 1, System.currentTimeMillis())
    val up_pressed_new_pos_2 = UpPressedEvent("1", sessionId, emptyList(), emptyList(), 2, System.currentTimeMillis())

    val down_event_new_pos_0 = DownPressedEvent("1", sessionId, emptyList(), emptyList(), 0, System.currentTimeMillis())
    val down_event_new_pos_1 = DownPressedEvent("1", sessionId, emptyList(), emptyList(), 1, System.currentTimeMillis())
    val down_event_new_pos_2 = DownPressedEvent("1", sessionId, emptyList(), emptyList(), 2, System.currentTimeMillis())

    val backspace_event_pos_0_left_0_1_2 = BackspaceEvent("1", sessionId, listOf(0, 1, 2), emptyList(), 0, 1, System.currentTimeMillis())
    val backspace_event_pos_0_left_1 = BackspaceEvent("1", sessionId, listOf(1), emptyList(), 0, 1, System.currentTimeMillis())

    val explicit_select_position_0 = ExplicitSelectEvent("1", sessionId, emptyList(), 0, 0, emptyList(), emptyMap(), System.currentTimeMillis())
    val explicit_select_position_2 = ExplicitSelectEvent("1", sessionId, emptyList(), 2, 2, emptyList(), emptyMap(), System.currentTimeMillis())
    val explicit_select_position_1 = ExplicitSelectEvent("1", sessionId, emptyList(), 1, 1, emptyList(), emptyMap(), System.currentTimeMillis())

    val selected_by_typing_0 = TypedSelectEvent("1", sessionId, emptyList(), 0, emptyList(), emptyMap(), System.currentTimeMillis())
    val selected_by_typing_1 = TypedSelectEvent("1", sessionId, emptyList(), 1, emptyList(), emptyMap(), System.currentTimeMillis())

}