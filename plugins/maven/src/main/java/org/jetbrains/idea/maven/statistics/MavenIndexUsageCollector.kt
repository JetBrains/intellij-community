// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object MavenIndexUsageCollector : CounterUsagesCollector() {
  val GROUP = EventLogGroup("maven.indexing", 1)

  @JvmField
  val IS_LOCAL = EventFields.Boolean("is_local")

  @JvmField
  val IS_CENTRAL = EventFields.Boolean("is_central")

  @JvmField
  val IS_PRIVATE_REMOTE = EventFields.Boolean("is_private")

  @JvmField
  val IS_SUCCESS = EventFields.Boolean("is_success")

  @JvmField
  val MANUAL = EventFields.Boolean("manual")

  @JvmField
  val INDEX_UPDATE = GROUP.registerIdeActivity("index_update",
                                               finishEventAdditionalFields = arrayOf(IS_LOCAL,
                                                                                     IS_CENTRAL,
                                                                                     IS_PRIVATE_REMOTE,
                                                                                     IS_SUCCESS,
                                                                                     MANUAL))

  @JvmField
  val INDEX_BROKEN = GROUP.registerEvent("index_open")

  @JvmField
  val INDEX_OPENED = GROUP.registerEvent("index_open", IS_LOCAL, IS_CENTRAL, IS_PRIVATE_REMOTE)

  @JvmField
  val ADD_ARTIFACT_FROM_POM = GROUP.registerEvent("artifact_from_pom_added")

  override fun getGroup(): EventLogGroup = GROUP

}
