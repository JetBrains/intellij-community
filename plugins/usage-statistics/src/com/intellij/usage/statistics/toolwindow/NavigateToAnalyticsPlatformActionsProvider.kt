package com.intellij.usage.statistics.toolwindow

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.intellij.internal.statistic.eventLog.EventLogSystemEvents.SYSTEM_EVENTS
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.toolwindow.StatisticsLogGroupActionsProvider
import com.intellij.openapi.actionSystem.AnAction

internal class NavigateToAnalyticsPlatformActionsProvider : StatisticsLogGroupActionsProvider {

  override fun getActions(groupId: String, eventId: String, eventData: String): List<AnAction> {
    val validationResults = ValidationResultType.values().mapTo(HashSet()) { it.description }
    if (groupId in validationResults) return emptyList()
    val actions = mutableListOf<AnAction>()
    if (!SYSTEM_EVENTS.contains(eventId) && eventId !in validationResults) {
      actions.add(OpenReportByEventAction(groupId, eventId))
      actions.addAll(createOpenReportWithFilterAction(groupId, eventId, eventData, validationResults))
    }
    actions.add(OpenReportByGroupAction(groupId))
    return actions
  }

  private fun createOpenReportWithFilterAction(groupId: String,
                                               eventId: String,
                                               eventData: String,
                                               validationResults: Set<String>): List<AnAction> {
    val fields = GroupsDescriptionsHolder.getInstance().getAutoSelectedFields(groupId, eventId)
    val jsonObject = readEventData(eventData) ?: return emptyList()
    val fieldValues = hashSetOf<String>()
    for (field in fields) {
      val fieldValue = jsonObject.get(field)?.asString
      if (fieldValue == null || fieldValue in validationResults) {
        continue
      }
      fieldValues.add(fieldValue)
    }
    return fieldValues.map { OpenEventReportWithFilterAction(groupId, eventId, it) }
  }

  private fun readEventData(eventData: String): JsonObject? {
    try {
      return Gson().fromJson(eventData, JsonObject::class.java)
    }
    catch (e: JsonSyntaxException) {
      return null
    }
  }
}
