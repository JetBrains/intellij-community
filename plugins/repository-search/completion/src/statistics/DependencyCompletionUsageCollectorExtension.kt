@file:ApiStatus.Experimental

package com.intellij.repository.search.completion.statistics

import com.intellij.codeInsight.lookup.impl.LookupResultDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageTracker
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension
import com.intellij.openapi.util.Key
import com.intellij.repository.search.completion.api.BaseDependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.util.asSafely
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

private val IS_AUTO_POPUP = EventFields.Boolean(
  "bt_dep_is_auto_popup",
  "True if the completion was triggered by auto popup, false if it was invoked manually"
)

private val CONTRIBUTION_SOURCE = EventFields.Enum<DependencyCompletionContributionSource>(
  "bt_dep_contribution_source",
  "Source of the dependency completion contribution."
)

val BT_COMPLETION_IS_AUTO_POPUP: Key<Boolean> = Key.create("BT_COMPLETION_IS_AUTO_POPUP")

/**
 * Declares additional fields that can be reported with the "finished" event of "completion" FUS group.
 * Version of [com.intellij.codeInsight.lookup.impl.LookupUsageTracker.GROUP] should be incremented every time any field is changed there.
 */
internal class DependencyCompletionUsageCollectorExtension : FeatureUsageCollectorExtension {
  override fun getGroupId(): @NonNls String = LookupUsageTracker.GROUP_ID

  override fun getEventId(): String = LookupUsageTracker.FINISHED_EVENT_ID

  override fun getExtensionFields(): List<EventField<*>> = listOf(
    IS_AUTO_POPUP,
    CONTRIBUTION_SOURCE,
  )
}

/**
 * Provides additional data for the "finished" event of "completion" FUS group.
 * Any fields reported there should be declared in [DependencyCompletionUsageCollectorExtension].
 */
internal class DependencyCompletionUsageDescriptor : LookupUsageDescriptor {
  override fun getExtensionKey(): String = "bt_dep"

  override fun getAdditionalUsageData(lookupResultDescriptor: LookupResultDescriptor): List<EventPair<*>> {
    val selectedItem = lookupResultDescriptor.selectedItem

    // look at all items in case the completion was canceled
    val anyItem = selectedItem ?: lookupResultDescriptor.lookup.items.firstOrNull {
      it.getUserData(BT_COMPLETION_IS_AUTO_POPUP) != null
    }
    val autoPopup = anyItem?.getUserData(BT_COMPLETION_IS_AUTO_POPUP)?.let { isAutoPopup ->
      IS_AUTO_POPUP with isAutoPopup
    }

    val contributionSource = selectedItem?.`object`.asSafely<BaseDependencyCompletionResult>()?.let { completionResult ->
      CONTRIBUTION_SOURCE with completionResult.source
    }

    return listOfNotNull(autoPopup, contributionSource)
  }
}
