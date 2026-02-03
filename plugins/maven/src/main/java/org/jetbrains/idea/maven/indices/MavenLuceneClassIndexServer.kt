// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.openapi.progress.ProcessCanceledException
import org.jetbrains.idea.maven.model.MavenArtifactInfo
import org.jetbrains.idea.maven.model.MavenIndexId
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import org.jetbrains.idea.maven.server.AddArtifactResponse
import org.jetbrains.idea.maven.server.MavenIndexerWrapper
import org.jetbrains.idea.maven.server.MavenServerIndexerException
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import java.nio.file.Path

class MavenLuceneClassIndexServer(private val myRepo: MavenRepositoryInfo,
                                  private val myIndexId: MavenIndexId,
                                  private val myNexusIndexer: MavenIndexerWrapper) : MavenUpdatableIndex, MavenSearchIndex {

  private var myUpdateTimestamp: Long = -1

  override fun close(releaseIndexContext: Boolean) {
    try {
      if (releaseIndexContext) myNexusIndexer.releaseIndex(myIndexId)
    }
    catch (e: MavenServerIndexerException) {
      MavenLog.LOG.warn(e)
    }
  }

  override fun getFailureMessage(): String {
    return ""
  }

  override fun search(pattern: String, maxResult: Int): Set<MavenArtifactInfo> {
    return myNexusIndexer.search(myIndexId, pattern, maxResult)
  }

  override fun updateOrRepair(fullUpdate: Boolean, progress: MavenProgressIndicator, explicit: Boolean) {
    myNexusIndexer.updateIndex(myIndexId, progress, explicit)
    myUpdateTimestamp = System.currentTimeMillis()
  }

  override fun tryAddArtifacts(artifactFiles: Collection<Path>): List<AddArtifactResponse> {
    try {
      return myNexusIndexer.addArtifacts(myIndexId, artifactFiles)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Exception) {
      MavenLog.LOG.error("exception adding artifacts into index $myIndexId")
      return artifactFiles.map {
        AddArtifactResponse(it.toFile(), null)
      }
    }

  }

  override fun getRepository(): MavenRepositoryInfo {
    return myRepo
  }
}
