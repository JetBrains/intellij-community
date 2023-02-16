// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.replaceService
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenModel
import org.jetbrains.idea.maven.server.*
import java.nio.file.Path
import java.rmi.ConnectException

abstract class MavenProjectReaderConnectorsTestCase : MavenProjectReaderTestCase() {
  fun <R> withStoppedConnector(action: () -> R): R {
    Disposer.newDisposable().use { disposable ->
      val factory = object : MavenServerManager.MavenServerConnectorFactoryImpl() {
        override fun create(project: Project,
                            jdk: Sdk,
                            vmOptions: String,
                            debugPort: Int?,
                            mavenDistribution: MavenDistribution,
                            multimoduleDirectory: String): MavenServerConnector {
          return StoppedMavenServerConnector()
        }
      }
      ApplicationManager.getApplication().replaceService(MavenServerManager.MavenServerConnectorFactory::class.java, factory, disposable)
      return action()
    }
  }

  fun <R> withStoppedConnectorOnce(action: () -> R): R {
    Disposer.newDisposable().use { disposable ->
      val factory = object : MavenServerManager.MavenServerConnectorFactoryImpl() {
        private var returnStoppedConnector = true
        override fun create(project: Project,
                            jdk: Sdk,
                            vmOptions: String,
                            debugPort: Int?,
                            mavenDistribution: MavenDistribution,
                            multimoduleDirectory: String): MavenServerConnector {
          if (returnStoppedConnector) {
            returnStoppedConnector = false
            return StoppedMavenServerConnector()
          }
          return super.create(project, jdk, vmOptions, debugPort, mavenDistribution, multimoduleDirectory)
        }
      }
      ApplicationManager.getApplication().replaceService(MavenServerManager.MavenServerConnectorFactory::class.java, factory, disposable)
      return action()
    }
  }

  class StoppedMavenServerConnector : MavenServerConnector {
    override fun createIndexer(): MavenServerIndexer {
      throw RuntimeException("not implemented")
    }

    override fun interpolateAndAlignModel(model: MavenModel?, basedir: Path?): MavenModel {
      throw ConnectException("Cannot reconnect")
    }

    override fun assembleInheritance(model: MavenModel?, parentModel: MavenModel?): MavenModel {
      throw ConnectException("Cannot reconnect")
    }


    override fun dispose() {
    }

    override fun isCompatibleWith(jdk: Sdk?, vmOptions: String?, distribution: MavenDistribution?): Boolean {
      return true
    }

    override fun isNew(): Boolean {
      return false
    }

    override fun connect() {
    }

    override fun addMultimoduleDir(multimoduleDirectory: String?): Boolean {
      throw RuntimeException("not implemented")
    }

    override fun createEmbedder(settings: MavenEmbedderSettings?): MavenServerEmbedder {
      throw RuntimeException("not implemented")
    }

    override fun applyProfiles(model: MavenModel?,
                               basedir: Path?,
                               explicitProfiles: MavenExplicitProfiles?,
                               alwaysOnProfiles: MutableCollection<String>?): ProfileApplicationResult {
      throw ConnectException("Cannot reconnect")
    }

    override fun ping(): Boolean {
      return false
    }

    override fun getSupportType(): String {
      throw RuntimeException("not implemented")
    }

    override fun getState(): MavenServerConnector.State {
      return MavenServerConnector.State.STOPPED
    }

    override fun checkConnected(): Boolean {
      return false
    }

    override fun stop(wait: Boolean) {
    }

    override fun getJdk(): Sdk {
      throw RuntimeException("not implemented")
    }

    override fun getMavenDistribution(): MavenDistribution {
      throw RuntimeException("not implemented")
    }

    override fun getVMOptions(): String {
      throw RuntimeException("not implemented")
    }

    override fun getProject(): Project? {
      throw RuntimeException("not implemented")
    }

    override fun getMultimoduleDirectories(): MutableList<String> {
      throw RuntimeException("not implemented")
    }

  }
}