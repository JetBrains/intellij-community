package com.intellij.mermaid.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import org.jetbrains.annotations.NonNls
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters

private fun obtainEventLogGroupConstructor(): (@NonNls String, Int) -> EventLogGroup {
  val constructor = EventLogGroup::class.primaryConstructor
  checkNotNull(constructor) { "Failed to obtain primary constructor for ${EventLogGroup::class.qualifiedName}" }
  val parameters = constructor.valueParameters
  // class EventLogGroup @JvmOverloads constructor(
  //   @NonNls @EventIdName val id: String,
  //   val version: Int,
  //   val recorder: String = "FUS",
  //   val description: String? = null,      <--- Added in 241; leads to compatibility issues.
  //   val marker: DefaultConstructorMarker  <--- Implicit.
  // )
  check(parameters.size >= 2) { "Unexpected argument count for $constructor: ${parameters.size}" }
  val (id, version) = parameters
  check(id.type.classifier == String::class) { "Unexpected type of 'id' argument: ${id.type.classifier}" }
  check(version.type.classifier == Int::class) { "Unexpected type of 'version' argument: ${version.type.classifier}" }
  return { idValue: @NonNls String, versionValue: Int ->
    constructor.callBy(mapOf(id to idValue, version to versionValue))
  }
}

internal fun createEventLogGroup(id: @NonNls String, version: Int): EventLogGroup {
  return obtainEventLogGroupConstructor().invoke(id, version)
}
