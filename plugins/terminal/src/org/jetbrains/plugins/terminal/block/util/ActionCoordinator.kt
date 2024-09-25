// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.util

import java.util.Collections


/**
 * Matches action start and finish events. Allows defining callback when action is finished.
 *
 * @param ID Identifier of the action. If events are added with the same id, then behavior is undefined.
 * @param DATA Any value that is submitted on action start and available on action finish.
 * @param capacity To avoid memory leaks. On capacity overflow, the implementation can drop events with [onActionDiscarded] call.
 * @param onActionComplete When action started and then finished. This callback should be lightweight.
 * @param onActionDiscarded When action is started and then deleted without a finish event. This callback should be lightweight.
 * @param onActionUnknown When a finish event is received without a matching start event. This callback should be lightweight.
 */
internal class ActionCoordinator<ID, DATA>(
  private val capacity: Int = 100,
  private val onActionComplete: (ID, DATA) -> Unit,
  private val onActionDiscarded: (ID, DATA) -> Unit,
  private val onActionUnknown: (ID) -> Unit,
) {

  /**
   * Map, which drops eldest element on [capacity] overflow.
   * Synchronized for thread safety.
   */
  private val storage: MutableMap<ID, DATA> = Collections.synchronizedMap(
    object : LinkedHashMap<ID, DATA>(capacity) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ID, DATA>?): Boolean {
        val removeEldestEntry = size > capacity

        if (removeEldestEntry && eldest != null) {
          onActionDiscarded(eldest.key, eldest.value)
        }

        return removeEldestEntry
      }
    }
  )

  /**
   * Registers that action has started.
   * If events are added with the same id until they are finished, then behavior is undefined.
   * Data will be available on action finish event, unless it is discarded due to capacity overflow.
   */
  fun started(actionId: ID, actionData: DATA) {
    storage.compute(actionId) { _, existingData: DATA? ->
      if (existingData != null) {
        // Do not override the key. Discard now.
        onActionDiscarded(actionId, actionData)
        existingData
      } else {
        actionData
      }
    }
  }

  /**
   * Registers that action has finished and calls [onActionComplete] callback.
   * If the action has unknown id, then calls [onActionUnknown] callback.
   * Non-idempotent: Repeatable call of this method for the same action id works only the first time.
   */
  fun finished(actionId: ID) {
    storage.remove(actionId)?.let { onActionComplete(actionId, it) } ?: onActionUnknown(actionId)
  }

}
