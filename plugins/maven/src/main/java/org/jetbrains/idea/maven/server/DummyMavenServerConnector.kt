// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.NotNull
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.server.security.MavenToken
import java.io.File


class DummyMavenServerConnector(project: @NotNull Project,
                                val manager: @NotNull MavenServerManager,
                                jdk: @NotNull Sdk,
                                vmOptions: @NotNull String,
                                mavenDistribution: @NotNull MavenDistribution,
                                multimoduleDirectory: @NotNull String) : MavenServerConnector(project, manager, jdk, vmOptions,
                                                                                              mavenDistribution, multimoduleDirectory) {
  override fun isNew() = false

  override fun isCompatibleWith(jdk: Sdk?, vmOptions: String?, distribution: MavenDistribution?) = true

  override fun connect() {
  }

  override fun getServer(): MavenServer {
    return DummyMavenServer(myProject)
  }

  override fun stop(wait: Boolean) {
  }

  override fun getSupportType() = MavenConfigurableBundle.message("connector.ui.dummy")

  override fun getState() = State.RUNNING

  override fun checkConnected() = true

  companion object {

    @JvmStatic
    fun MavenServerConnector.isDummy(): Boolean {
      return this is DummyMavenServerConnector
    }
  }
}

class DummyMavenServer(val project: Project) : MavenServer {


  override fun createEmbedder(settings: MavenEmbedderSettings?, token: MavenToken?): MavenServerEmbedder {
    return UntrustedDummyEmbedder(project)
  }

  override fun createIndexer(token: MavenToken?): MavenServerIndexer {
    return DummyIndexer()
  }

  override fun interpolateAndAlignModel(model: MavenModel, basedir: File?, token: MavenToken?): MavenModel {
    return model
  }

  override fun assembleInheritance(model: MavenModel, parentModel: MavenModel?, token: MavenToken?): MavenModel {
    return model
  }

  override fun applyProfiles(model: MavenModel,
                             basedir: File?,
                             explicitProfiles: MavenExplicitProfiles?,
                             alwaysOnProfiles: Collection<String>?,
                             token: MavenToken?): ProfileApplicationResult {
    return ProfileApplicationResult(model, MavenExplicitProfiles.NONE)
  }

  override fun createPullLogger(token: MavenToken?): MavenPullServerLogger? {
    return null
  }

  override fun createPullDownloadListener(token: MavenToken?): MavenPullDownloadListener? {
    return null
  }
}

class DummyIndexer : MavenServerIndexer {

  override fun releaseIndex(id: MavenIndexId, token: MavenToken?) {
  }

  override fun getIndexCount(token: MavenToken?): Int {
    return 0
  }

  override fun updateIndex(id: MavenIndexId, indicator: MavenServerProgressIndicator?, token: MavenToken?) {
  }

  override fun processArtifacts(indexId: MavenIndexId, startFrom: Int, token: MavenToken?): List<IndexedMavenId>? = null

  override fun addArtifact(indexId: MavenIndexId, artifactFile: File?, token: MavenToken?): IndexedMavenId {
    return IndexedMavenId(null, null, null, null, null)
  }

  override fun search(indexId: MavenIndexId, query: String, maxResult: Int, token: MavenToken?): Set<MavenArtifactInfo> {
    return emptySet()
  }

  override fun getInternalArchetypes(token: MavenToken?): Collection<MavenArchetype> {
    return emptySet()
  }

  override fun release(token: MavenToken?) {
  }

  override fun indexExists(dir: File?, token: MavenToken?): Boolean {
    return false
  }
}

