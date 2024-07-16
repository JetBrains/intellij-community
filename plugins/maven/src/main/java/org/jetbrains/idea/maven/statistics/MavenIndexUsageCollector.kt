// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object MavenIndexUsageCollector : CounterUsagesCollector() {
  val GROUP = EventLogGroup("maven.indexing", 3)

  @JvmField
  val IS_LOCAL = EventFields.Boolean("is_local", "True if index for local repository")

  @JvmField
  val IS_CENTRAL = EventFields.Boolean("is_central", "True if index for central repository")

  @JvmField
  val IS_PRIVATE_REMOTE = EventFields.Boolean("is_private", "True if index for remote repository and it is not maven cenral")

  @JvmField
  val IS_SUCCESS = EventFields.Boolean("is_success", "True is index for central repository")

  @JvmField
  val MANUAL = EventFields.Boolean("manual", "True if action triggered explicitly by user")

  @JvmField
  val GROUPS_COUNT = EventFields.RoundedInt("groups_count", "Number of distinct groupIds")

  @JvmField
  val ARTIFACTS_COUNT = EventFields.RoundedInt("artifacts_count", "Number of scanned artifacts")

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
  val INDEX_BROKEN = GROUP.registerEvent("index.broken", "Sent when broken index detected")

  @JvmField
  val INDEX_OPENED = GROUP.registerEvent("index.open", IS_LOCAL, IS_CENTRAL,
                                         IS_PRIVATE_REMOTE, "Sent when index opened")

  @JvmField
  val ADD_ARTIFACT_FROM_POM = GROUP.registerEvent("artifact.from.pom.added", "triggered if index did't contain the artifact, while it is present on disk and was added to index by highlight process")

  override fun getGroup(): EventLogGroup = GROUP

}
