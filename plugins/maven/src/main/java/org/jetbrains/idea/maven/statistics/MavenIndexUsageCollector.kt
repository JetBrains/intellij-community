// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object MavenIndexUsageCollector : CounterUsagesCollector() {
  val GROUP = EventLogGroup("maven.indexing", 3)

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
  val GROUPS_COUNT = EventFields.RoundedInt("groups_count")

  @JvmField
  val ARTIFACTS_COUNT = EventFields.RoundedInt("artifacts_count")

  @JvmField
  val INDEX_UPDATE = GROUP.registerIdeActivity("index.update",
                                               finishEventAdditionalFields = arrayOf(IS_LOCAL,
                                                                                     IS_CENTRAL,
                                                                                     IS_PRIVATE_REMOTE,
                                                                                     IS_SUCCESS,
                                                                                     MANUAL))

  @JvmField
  val GAV_INDEX_UPDATE = GROUP.registerIdeActivity("gav.index.update",
                                                   finishEventAdditionalFields = arrayOf(MANUAL,
                                                                                         IS_SUCCESS,
                                                                                         GROUPS_COUNT,
                                                                                         ARTIFACTS_COUNT))


  @JvmField
  val INDEX_BROKEN = GROUP.registerEvent("index.broken")

  @JvmField
  val INDEX_OPENED = GROUP.registerEvent("index.open", IS_LOCAL, IS_CENTRAL,
                                         IS_PRIVATE_REMOTE)

  @JvmField
  val ADD_ARTIFACT_FROM_POM = GROUP.registerEvent("artifact.from.pom.added")

  override fun getGroup(): EventLogGroup = GROUP

}
