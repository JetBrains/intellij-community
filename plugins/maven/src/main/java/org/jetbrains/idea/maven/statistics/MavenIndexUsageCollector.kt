// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.intellij.internal.statistic.eventLog.events.RoundedIntEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object MavenIndexUsageCollector : CounterUsagesCollector() {
  val GROUP: EventLogGroup = EventLogGroup("maven.indexing", 3)

  @JvmField
  val IS_LOCAL: BooleanEventField = EventFields.Boolean("is_local", "True if index for local repository")

  @JvmField
  val IS_CENTRAL: BooleanEventField = EventFields.Boolean("is_central", "True if index for central repository")

  @JvmField
  val IS_PRIVATE_REMOTE: BooleanEventField = EventFields.Boolean("is_private", "True if index for remote repository and it is not maven cenral")

  @JvmField
  val IS_SUCCESS: BooleanEventField = EventFields.Boolean("is_success", "True is index for central repository")

  @JvmField
  val MANUAL: BooleanEventField = EventFields.Boolean("manual", "True if action triggered explicitly by user")

  @JvmField
  val GROUPS_COUNT: RoundedIntEventField = EventFields.RoundedInt("groups_count", "Number of distinct groupIds")

  @JvmField
  val ARTIFACTS_COUNT: RoundedIntEventField = EventFields.RoundedInt("artifacts_count", "Number of scanned artifacts")

  @JvmField
  val INDEX_UPDATE: IdeActivityDefinition = GROUP.registerIdeActivity(
    "index.update",
    finishEventAdditionalFields = arrayOf(IS_LOCAL, IS_CENTRAL, IS_PRIVATE_REMOTE, IS_SUCCESS, MANUAL))

  @JvmField
  val GAV_INDEX_UPDATE: IdeActivityDefinition = GROUP.registerIdeActivity(
    "gav.index.update",
    finishEventAdditionalFields = arrayOf(MANUAL, IS_SUCCESS, GROUPS_COUNT, ARTIFACTS_COUNT))

  @JvmField
  val INDEX_BROKEN: EventId = GROUP.registerEvent("index.broken")

  @JvmField
  val INDEX_OPENED: EventId3<Boolean, Boolean, Boolean> = GROUP.registerEvent("index.open", IS_LOCAL, IS_CENTRAL, IS_PRIVATE_REMOTE)

  @JvmField
  val ADD_ARTIFACT_FROM_POM: EventId = GROUP.registerEvent("artifact.from.pom.added")

  override fun getGroup(): EventLogGroup = GROUP
}
