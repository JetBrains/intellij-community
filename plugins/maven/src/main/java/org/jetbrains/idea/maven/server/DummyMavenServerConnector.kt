// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import com.intellij.execution.rmi.IdeaWatchdog
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.server.MavenServerConnector.State
import org.jetbrains.idea.maven.server.security.MavenToken
import java.io.File
import java.nio.file.Path
import java.rmi.RemoteException


class DummyMavenServerConnector(override val project: Project,
                                jdk: Sdk,
                                vmOptions: String,
                                mavenDistribution: MavenDistribution,
                                multimoduleDirectory: String) :
  AbstractMavenServerConnector(project, jdk, vmOptions, mavenDistribution, multimoduleDirectory) {

  override val state: State
    get() = State.RUNNING

  override val supportType: String
    get() = MavenConfigurableBundle.message("connector.ui.dummy")


  override fun isNew() = false

  override fun isCompatibleWith(jdk: Sdk, vmOptions: String, distribution: MavenDistribution) = true

  override fun connect() {
  }

  override fun getServerBlocking(): MavenServer = DummyMavenServer(project)

  override suspend fun getServer(): MavenServer = DummyMavenServer(project)

  override fun pingBlocking(): Boolean {
    return true
  }

  override suspend fun ping(): Boolean {
    return true
  }

  override fun stop(wait: Boolean) {
  }

  override fun checkConnected() = true

  override fun <R> perform(r: () -> R): R {
    return try {
      r()
    }
    catch (e: RemoteException) {
      throw RuntimeException(e)
    }
  }

  companion object {

    @JvmStatic
    fun MavenServerConnector.isDummy(): Boolean {
      return this is DummyMavenServerConnector
    }
  }
}

class DummyMavenServer(val project: Project) : MavenServer {
  private lateinit var watchdog: IdeaWatchdog
  override fun setWatchdog(watchdog: IdeaWatchdog) {
    this.watchdog = watchdog
  }

  override fun createEmbedder(settings: MavenEmbedderSettings?, token: MavenToken?): MavenServerEmbedder {
    return UntrustedDummyEmbedder(project)
  }

  override fun createIndexer(token: MavenToken?): MavenServerIndexer {
    return DummyIndexer()
  }

  override fun interpolateAndAlignModel(model: MavenModel, basedir: File?, pomDir: File?, token: MavenToken?): MavenModel {
    return model
  }

  override fun assembleInheritance(model: MavenModel, parentModel: MavenModel?, token: MavenToken?): MavenModel {
    return model
  }

  override fun applyProfiles(model: MavenModel,
                             basedir: File?,
                             explicitProfiles: MavenExplicitProfiles?,
                             alwaysOnProfiles: HashSet<String>?,
                             token: MavenToken?): ProfileApplicationResult {
    return ProfileApplicationResult(model, MavenExplicitProfiles.NONE)
  }

  override fun createPullLogger(token: MavenToken?): MavenPullServerLogger? {
    return null
  }

  override fun createPullDownloadListener(token: MavenToken?): MavenPullDownloadListener? {
    return null
  }

  override fun ping(token: MavenToken?): Boolean {
    return true
  }

  override fun getDebugStatus(clean: Boolean): MavenServerStatus {
    throw RuntimeException("not supported")
  }
}

class DummyIndexer : MavenServerIndexer {

  override fun releaseIndex(id: MavenIndexId, token: MavenToken?) {
  }

  override fun getIndexCount(token: MavenToken?): Int {
    return 0
  }

  override fun updateIndex(id: MavenIndexId,
                           indicator: MavenServerProgressIndicator?,
                           multithreaded: Boolean,
                           token: MavenToken?) {
  }

  override fun processArtifacts(indexId: MavenIndexId, startFrom: Int, token: MavenToken?): ArrayList<IndexedMavenId>? = null

  override fun addArtifacts(indexId: MavenIndexId, artifactFiles: ArrayList<Path>, token: MavenToken): ArrayList<AddArtifactResponse> {
    val responses = ArrayList<AddArtifactResponse>()
    for (artifactFile in artifactFiles) {
      responses.add(AddArtifactResponse(artifactFile.toFile(), IndexedMavenId(null, null, null, null, null)))
    }
    return responses
  }

  override fun search(indexId: MavenIndexId, query: String, maxResult: Int, token: MavenToken?): HashSet<MavenArtifactInfo> {
    return HashSet()
  }

  override fun getInternalArchetypes(token: MavenToken?): HashSet<MavenArchetype> {
    return HashSet()
  }

  override fun release(token: MavenToken?) {
  }

  override fun indexExists(dir: Path?, token: MavenToken?): Boolean {
    return false
  }
}

