package com.intellij.usage.statistics.toolwindow

internal data class GroupsDescriptions(val groups: List<GroupDescription>?)

internal data class GroupDescription(val id: String?, val products: List<String>?, val context: GroupContext?)

internal data class GroupContext(val event_data_1: List<EventDataContext>?,
                        val event_data_2: List<EventDataContext>?,
                        val event_data_3: List<EventDataContext>?,
                        val event_data_4: List<EventDataContext>?,
                        val event_data_5: List<EventDataContext>?,
                        val event_data_6: List<EventDataContext>?,
                        val event_data_7: List<EventDataContext>?,
                        val event_data_8: List<EventDataContext>?,
                        val event_data_9: List<EventDataContext>?,
                        val event_data_10: List<EventDataContext>?)

internal data class EventDataContext(val event_id: List<String>?, val path: String?, val auto_select: Boolean?)
