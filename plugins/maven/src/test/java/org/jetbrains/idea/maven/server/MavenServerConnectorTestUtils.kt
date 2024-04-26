// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.replaceService
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenModel
import java.nio.file.Path
import java.rmi.ConnectException

suspend fun <R> withStoppedConnector(action: suspend () -> R): R {
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

suspend fun <R> withStoppedConnectorOnce(action: suspend () -> R): R {
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

suspend fun <R> withCompatibleConnector(action: suspend () -> R): R {
  Disposer.newDisposable().use { disposable ->
    val factory = object : MavenServerManager.MavenServerConnectorFactoryImpl() {
      override fun create(project: Project,
                          jdk: Sdk,
                          vmOptions: String,
                          debugPort: Int?,
                          mavenDistribution: MavenDistribution,
                          multimoduleDirectory: String): MavenServerConnector {
        return CompatibleMavenServerConnector(project)
      }
    }
    ApplicationManager.getApplication().replaceService(MavenServerManager.MavenServerConnectorFactory::class.java, factory, disposable)
    return action()
  }
}

private class CompatibleMavenServerConnector(override val project: Project) : MavenServerConnector {
  override val supportType: String
    get() = throw RuntimeException("not implemented")
  override val state: MavenServerConnector.State
    get() = throw RuntimeException("not implemented")
  override val jdk: Sdk
    get() = throw RuntimeException("not implemented")
  override val mavenDistribution: MavenDistribution
    get() = throw RuntimeException("not implemented")
  override val vmOptions: String
    get() = throw RuntimeException("not implemented")
  override val multimoduleDirectories: List<String>
    get() = throw RuntimeException("not implemented")

  override fun isNew(): Boolean {
    return false
  }

  override fun connect() {
  }

  override fun addMultimoduleDir(multimoduleDirectory: String): Boolean {
    return true
  }

  override fun createEmbedder(settings: MavenEmbedderSettings): MavenServerEmbedder {
    throw RuntimeException("not implemented")
  }

  override fun createIndexer(): MavenServerIndexer {
    throw RuntimeException("not implemented")
  }

  override suspend fun interpolateAndAlignModel(model: MavenModel, basedir: Path, pomDir: Path): MavenModel {
    throw RuntimeException("not implemented")
  }

  override suspend fun assembleInheritance(model: MavenModel, parentModel: MavenModel): MavenModel {
    throw RuntimeException("not implemented")
  }

    override suspend fun applyProfiles(model: MavenModel,
                                       basedir: Path,
                                       explicitProfiles: MavenExplicitProfiles,
                                       alwaysOnProfiles: Collection<String>): ProfileApplicationResult {
    throw RuntimeException("not implemented")
  }

  override fun pingBlocking(): Boolean {
    return true
  }

  override suspend fun ping(): Boolean {
    return true
  }

  override fun checkConnected(): Boolean {
    return true
  }

  override fun stop(wait: Boolean) {
  }

  override fun getDebugStatus(clean: Boolean): MavenServerStatus {
    throw RuntimeException("not implemented")
  }

  override fun dispose() {
  }

  override fun isCompatibleWith(jdk: Sdk, vmOptions: String, distribution: MavenDistribution): Boolean {
    return true
  }
}

private class StoppedMavenServerConnector : MavenServerConnector {
  override val supportType: String
    get() = throw RuntimeException("not implemented")
  override val state: MavenServerConnector.State
    get() = throw RuntimeException("not implemented")
  override val jdk: Sdk
    get() = throw RuntimeException("not implemented")
  override val mavenDistribution: MavenDistribution
    get() = throw RuntimeException("not implemented")
  override val vmOptions: String
    get() = throw RuntimeException("not implemented")
  override val project: Project
    get() = throw RuntimeException("not implemented")
  override val multimoduleDirectories: List<String>
    get() = throw RuntimeException("not implemented")

  override fun createIndexer(): MavenServerIndexer {
    throw RuntimeException("not implemented")
  }

  override suspend fun interpolateAndAlignModel(model: MavenModel, basedir: Path, pomDir: Path): MavenModel {
    throw ConnectException("Cannot reconnect")
  }

  override suspend fun assembleInheritance(model: MavenModel, parentModel: MavenModel): MavenModel {
    throw ConnectException("Cannot reconnect")
  }


  override fun dispose() {
  }

  override fun isCompatibleWith(jdk: Sdk, vmOptions: String, distribution: MavenDistribution): Boolean {
    return true
  }

  override fun isNew(): Boolean {
    return false
  }

  override fun connect() {
  }

  override fun addMultimoduleDir(multimoduleDirectory: String): Boolean {
    throw RuntimeException("not implemented")
  }

  override fun createEmbedder(settings: MavenEmbedderSettings): MavenServerEmbedder {
    throw RuntimeException("not implemented")
  }

  override suspend fun applyProfiles(model: MavenModel,
                                     basedir: Path,
                                     explicitProfiles: MavenExplicitProfiles,
                                     alwaysOnProfiles: Collection<String>): ProfileApplicationResult {
    throw ConnectException("Cannot reconnect")
  }

  override fun pingBlocking(): Boolean {
    return false
  }

  override suspend fun ping(): Boolean {
    return false
  }

  override fun checkConnected(): Boolean {
    return false
  }

  override fun stop(wait: Boolean) {
  }

  override fun getDebugStatus(clean: Boolean) : MavenServerStatus{
    throw RuntimeException("not implemented")
  }

}