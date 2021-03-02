// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.editor

import circlet.client.api.M2ChannelRecord
import circlet.m2.ChannelsVm
import circlet.platform.api.Ref
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.space.chat.editor.SpaceChatFile
import com.intellij.space.chat.model.api.SpaceChatHeaderDetails
import com.intellij.space.vcs.review.details.SpaceReviewChangesVm
import com.intellij.space.vcs.review.details.diff.SpaceDiffFile
import com.intellij.space.vcs.review.details.diff.SpaceDiffFileData
import com.intellij.space.vcs.review.details.diff.SpaceDiffFileId
import com.intellij.space.vcs.review.details.diff.SpaceDiffVm
import com.intellij.util.containers.ContainerUtil.createWeakValueMap
import libraries.coroutines.extra.Lifetime

@Service
internal class SpaceVirtualFilesManager(private val project: Project) : Disposable {
  // current time should be enough to distinguish the manager between launches
  private val sessionId = System.currentTimeMillis().toString()

  private val chatFiles = createWeakValueMap<String, SpaceChatFile>()
  private val diffFiles = createWeakValueMap<SpaceDiffFileId, SpaceDiffFile>()

  fun findChatFile(id: String) = chatFiles[id]
  fun findDiffFile(fileId: SpaceDiffFileId) = diffFiles[fileId]

  fun findOrCreateChatFile(
    @NlsSafe id: String,
    @NlsSafe path: String,
    @NlsContexts.TabTitle displayName: String,
    @NlsContexts.Tooltip tabTooltip: String,
    channelsVm: ChannelsVm,
    chatRecord: Ref<M2ChannelRecord>,
    headerDetailsBuilder: (Lifetime) -> SpaceChatHeaderDetails
  ): SpaceChatFile = chatFiles.getOrPut(id) {
    SpaceChatFile(sessionId, project.locationHash, id, path, displayName, tabTooltip, channelsVm, chatRecord, headerDetailsBuilder)
  }

  fun findOrCreateDiffFile(
    changesVm: runtime.reactive.Property<SpaceReviewChangesVm>,
    diffVm: SpaceDiffVm
  ): SpaceDiffFile = SpaceDiffFileId(diffVm.projectKey.key, diffVm.reviewKey, diffVm.reviewId).let { fileId ->
    diffFiles.getOrPut(fileId) { SpaceDiffFile(sessionId, project.locationHash, fileId, diffVm.reviewKey) }
      .apply { updateDiffPresentation(changesVm, diffVm) }
  }

  fun updateDiffPresentation(changesVm: runtime.reactive.Property<SpaceReviewChangesVm>,
                             diffVm: SpaceDiffVm) {
    val fileId = SpaceDiffFileId(diffVm.projectKey.key, diffVm.reviewKey, diffVm.reviewId)
    diffFiles[fileId]?.updateDiffFileData(SpaceDiffFileData(changesVm, diffVm))
  }

  override fun dispose() {
    chatFiles.clear()
    diffFiles.clear()
  }
}
