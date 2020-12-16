package com.intellij.usage.statistics.toolwindow

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.util.io.HttpRequests
import java.io.IOException

@Service
class GroupsDescriptionsHolder {
  private val lock = Object()

  // guarded by lock
  private var autoSelectedFields: Map<GroupEvent, Set<String>> = emptyMap()

  // guarded by lock
  private var isInitialized = false

  fun updateAutoSelectedFields() {
    val result = loadAutoSelectedFields()
    synchronized(lock) {
      autoSelectedFields = result
      isInitialized = true
    }
  }


  fun getAutoSelectedFields(groupId: String, eventId: String): Set<String> {
    synchronized(lock) {
      val fields = if (isInitialized) {
        autoSelectedFields
      }
      else defaultAutoSelectedFields
      return fields[GroupEvent(groupId, eventId)] ?: emptySet()
    }
  }

  private fun loadAutoSelectedFields(): Map<GroupEvent, Set<String>> {
    val groupsDescriptions = loadGroupsDescription()?.groups
                             ?: return defaultAutoSelectedFields
    val autoSelectedFields = hashMapOf<GroupEvent, MutableSet<String>>()
    val productCode = ApplicationInfo.getInstance().build.productCode
    for (group in groupsDescriptions) {
      val groupId = group.id ?: continue
      if (!group.products.orEmpty().contains(productCode)) continue
      fillAutoSelectedFields(autoSelectedFields, group.context?.event_data_1, groupId)
      fillAutoSelectedFields(autoSelectedFields, group.context?.event_data_2, groupId)
      fillAutoSelectedFields(autoSelectedFields, group.context?.event_data_3, groupId)
      fillAutoSelectedFields(autoSelectedFields, group.context?.event_data_4, groupId)
      fillAutoSelectedFields(autoSelectedFields, group.context?.event_data_5, groupId)
      fillAutoSelectedFields(autoSelectedFields, group.context?.event_data_6, groupId)
      fillAutoSelectedFields(autoSelectedFields, group.context?.event_data_7, groupId)
      fillAutoSelectedFields(autoSelectedFields, group.context?.event_data_8, groupId)
      fillAutoSelectedFields(autoSelectedFields, group.context?.event_data_9, groupId)
      fillAutoSelectedFields(autoSelectedFields, group.context?.event_data_10, groupId)
    }

    return if (autoSelectedFields.isEmpty()) defaultAutoSelectedFields else autoSelectedFields
  }

  private fun fillAutoSelectedFields(filters: MutableMap<GroupEvent, MutableSet<String>>,
                                     contexts: List<EventDataContext>?,
                                     groupId: String) {
    if (contexts == null) return
    for (context in contexts) {
      if (context.auto_select == true && context.path != null && context.event_id != null && context.event_id.isNotEmpty()) {
        for (event in context.event_id) {
          val groupEvent = GroupEvent(groupId, event)
          val autoSelectedFields = filters[groupEvent] ?: hashSetOf()
          autoSelectedFields.add(context.path)
          filters[groupEvent] = autoSelectedFields
        }
      }
    }
  }

  private fun loadGroupsDescription(): GroupsDescriptions? {
    try {
      val groupsDescription = HttpRequests.request(GROUPS_URL)
        .productNameAsUserAgent()
        .readString(null)
      return Gson().fromJson(groupsDescription, GroupsDescriptions::class.java)
    }
    catch (e: IOException) {
      return null
    }
    catch (e: JsonSyntaxException) {
      return null
    }
  }

  companion object {
    private const val GROUPS_URL = "https://resources.jetbrains.com/storage/fus/whitelist/tiger/groups.json"
    private val defaultAutoSelectedFields = hashMapOf(
      GroupEvent("actions", "action.invoked") to setOf("action_id"),
      GroupEvent("settings", "not.default") to setOf("component")
    )

    fun getInstance(): GroupsDescriptionsHolder = ApplicationManager.getApplication().getService(GroupsDescriptionsHolder::class.java)
  }

  data class GroupEvent(val group: String, val event: String)
}