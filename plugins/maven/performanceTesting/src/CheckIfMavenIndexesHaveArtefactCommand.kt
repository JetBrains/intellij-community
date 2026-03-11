// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.performanceTesting

import com.intellij.maven.performanceTesting.dto.MavenArchetypeInfo
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.idea.maven.indices.MavenClassSearchResult
import org.jetbrains.idea.maven.indices.MavenClassSearcher

/**
 * The command validates if maven indexes have an artifact and if not you can call [MavenIndexUpdateCommand]
 * Argument is serialized [com.intellij.maven.performanceTesting.dto.MavenArchetypeInfo] as json
 * Syntax: %checkIfMavenIndexesHaveArtefact serialized [com.intellij.maven.performanceTesting.dto.MavenArchetypeInfo]
 */
class CheckIfMavenIndexesHaveArtefactCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "checkIfMavenIndexesHaveArtefact"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val expectedArtifact = deserializeOptionsFromJson(extractCommandArgument(prefix), MavenArchetypeInfo::class.java)
    MavenClassSearcher()
      .search(project, "", 1000)
      .any {
        val actualInfo = (it as MavenClassSearchResult).searchResults
        actualInfo.artifactId == expectedArtifact.artefactId &&
        actualInfo.groupId == expectedArtifact.groupId &&
        actualInfo.version == expectedArtifact.version
      }
      .also {
        if (!it) {
          throw IllegalStateException("There is no artifact $expectedArtifact in maven indexes")
        }
      }
  }

  override fun getName(): String {
    return NAME
  }
}