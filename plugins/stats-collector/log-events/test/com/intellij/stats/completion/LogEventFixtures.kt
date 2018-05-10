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

package com.intellij.stats.completion

import com.intellij.stats.completion.events.*

@Suppress("unused")
object LogEventFixtures {
    
    val sessionId = "session-id-xxx"

    val completion_started_3_items_shown = CompletionStartedEvent("", "", "",
            "1", sessionId, "Java", true, 1, Fixtures.lookupList,
            Fixtures.userFactors, 0)

    val completion_cancelled = CompletionCancelledEvent("1", sessionId)

    val type_event_current_pos_0_left_ids_1_2 = TypeEvent("1", sessionId, listOf(1, 2), emptyList(), 0)
    val type_event_current_pos_0_left_ids_0_1 = TypeEvent("1", sessionId, listOf(0, 1), emptyList(), 0)
    val type_event_current_pos_0_left_id_0 = TypeEvent("1", sessionId, listOf(0), emptyList(), 0)

    val up_pressed_new_pos_0 = UpPressedEvent("1", sessionId, emptyList(), emptyList(), 0)
    val up_pressed_new_pos_1 = UpPressedEvent("1", sessionId, emptyList(), emptyList(), 1)
    val up_pressed_new_pos_2 = UpPressedEvent("1", sessionId, emptyList(), emptyList(), 2)

    val down_event_new_pos_0 = DownPressedEvent("1", sessionId, emptyList(), emptyList(), 0)
    val down_event_new_pos_1 = DownPressedEvent("1", sessionId, emptyList(), emptyList(), 1)
    val down_event_new_pos_2 = DownPressedEvent("1", sessionId, emptyList(), emptyList(), 2)

    val backspace_event_pos_0_left_0_1_2 = BackspaceEvent("1", sessionId, listOf(0, 1, 2), emptyList(), 0)
    val backspace_event_pos_0_left_1 = BackspaceEvent("1", sessionId, listOf(1), emptyList(), 0)

    val explicit_select_position_0 = ExplicitSelectEvent("1", sessionId, emptyList(), 0, 0, emptyList(), emptyMap())
    val explicit_select_position_2 = ExplicitSelectEvent("1", sessionId, emptyList(), 2, 2, emptyList(), emptyMap())
    val explicit_select_position_1 = ExplicitSelectEvent("1", sessionId, emptyList(), 1, 1, emptyList(), emptyMap())

    val selected_by_typing_0 = TypedSelectEvent("1", sessionId, emptyList(), 0, emptyList(), emptyMap())
    val selected_by_typing_1 = TypedSelectEvent("1", sessionId, emptyList(), 1, emptyList(), emptyMap())

}