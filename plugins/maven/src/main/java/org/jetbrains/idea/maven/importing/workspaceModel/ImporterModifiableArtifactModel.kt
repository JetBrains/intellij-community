// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.configurationStore.serialize
import com.intellij.java.workspace.entities.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.packaging.artifacts.*
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.impl.artifacts.ArtifactUtil
import com.intellij.packaging.impl.artifacts.workspacemodel.getArtifactProperties
import com.intellij.packaging.impl.elements.ArchivePackagingElement
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.LegacyBridgeJpsEntitySourceFactory
import org.jetbrains.jps.util.JpsPathUtil
import kotlin.collections.set

internal class ImporterModifiableArtifact(private val project: Project,
                                          private var name: String,
                                          private var artifactType: ArtifactType,
                                          private var outputUrl: VirtualFileUrl,
                                          private var rootElement: CompositePackagingElement<*>,
                                          private val externalSource: ProjectModelExternalSource?) : ModifiableArtifact {

  private val artifactProperties: MutableMap<String, ArtifactProperties<*>> = mutableMapOf()
  private var includeInProjectBuild = false
  private val contextData = UserDataHolderBase()

  init {
    val existingArtifact = WorkspaceModel.getInstance(project).currentSnapshot.resolve(ArtifactId(name))
    if (null != existingArtifact) {
      includeInProjectBuild = existingArtifact.includeInProjectBuild
    }
  }

  override fun <T : Any?> getUserData(key: Key<T>): T? = contextData.getUserData(key)
  override fun <T : Any?> putUserData(key: Key<T>, value: T?) = contextData.putUserData(key, value)
  override fun getExternalSource() = externalSource
  override fun getArtifactType() = artifactType
  override fun getName() = name
  override fun getRootElement() = rootElement
  override fun isBuildOnMake(): Boolean = includeInProjectBuild
  override fun getOutputPath(): String? = outputUrl.url?.let { JpsPathUtil.urlToPath(it) }
  override fun getOutputFile() = outputUrl.virtualFile
  fun getOutputUrl() = outputUrl

  override fun getOutputFilePath(): String? {
    val path = JpsPathUtil.urlToPath(outputUrl.url)
    return if (rootElement is ArchivePackagingElement) path + "/" + (rootElement as ArchivePackagingElement).archiveFileName else path
  }

  override fun setBuildOnMake(enabled: Boolean) {
    includeInProjectBuild = enabled
  }

  override fun setOutputPath(outputPath: String?) {
    val outputUrl = outputPath?.let { VirtualFileUrlManager.getInstance(project).fromUrl(VfsUtilCore.pathToUrl(it)) }
    this.outputUrl = outputUrl!!
  }

  override fun setName(name: String) {
    this.name = name
  }

  override fun setRootElement(root: CompositePackagingElement<*>) {
    rootElement = root
  }

  override fun getPropertiesProviders(): Collection<ArtifactPropertiesProvider> = ArtifactPropertiesProvider.getProviders().filter {
    it.isAvailableFor(this.artifactType)
  }

  override fun getProperties(propertiesProvider: ArtifactPropertiesProvider): ArtifactProperties<*>? {
    val providerId = propertiesProvider.id
    if (!artifactProperties.containsKey(providerId)) {
      if (propertiesProvider.isAvailableFor(this.artifactType)) {
        val existingArtifact = WorkspaceModel.getInstance(project).currentSnapshot.resolve(ArtifactId(name))

        val existingProperties =
          if (null != existingArtifact) getArtifactProperties(existingArtifact, artifactType, propertiesProvider) else null

        val properties = existingProperties ?: propertiesProvider.createProperties(artifactType)

        artifactProperties[providerId] = properties
      }
    }
    return artifactProperties[providerId]
  }

  override fun setProperties(provider: ArtifactPropertiesProvider, properties: ArtifactProperties<*>?) {
    artifactProperties[provider.id] = properties!!
  }

  override fun setArtifactType(selected: ArtifactType) {
    artifactType = selected
  }

  override fun toString(): String {
    return "ImporterModifiableArtifact(name='$name')"
  }

}

internal class ImporterModifiableArtifactModel(private val project: Project,
                                               private val storage: MutableEntityStorage) : ModifiableArtifactModel {
  private val artifacts = mutableListOf<ImporterModifiableArtifact>()

  override fun getArtifacts(): Array<Artifact> = artifacts.toTypedArray()
  override fun findArtifact(name: String): ModifiableArtifact? = artifacts.firstOrNull { it.name == name }
  override fun getArtifactByOriginal(artifact: Artifact): Artifact = artifact
  override fun getOriginalArtifact(artifact: Artifact): Artifact = artifact
  override fun getArtifactsByType(type: ArtifactType): MutableCollection<out Artifact> = artifacts.filter { it.artifactType == type }.toMutableList()
  override fun getAllArtifactsIncludingInvalid(): MutableList<out Artifact> = artifacts

  private fun generateUniqueName(baseName: String): String {
    return UniqueNameGenerator.generateUniqueName(baseName) { findArtifact(it) == null }
  }

  override fun addArtifact(name: String,
                           artifactType: ArtifactType,
                           rootElement: CompositePackagingElement<*>,
                           externalSource: ProjectModelExternalSource?): ModifiableArtifact {
    val uniqueName = generateUniqueName(name)

    val outputPath = ArtifactUtil.getDefaultArtifactOutputPath(uniqueName, project)
    val fileManager = VirtualFileUrlManager.getInstance(project)
    val outputUrl = outputPath?.let { fileManager.fromUrl(VfsUtilCore.pathToUrl(it)) }

    val artifact = ImporterModifiableArtifact(project, uniqueName, artifactType, outputUrl!!, rootElement, externalSource)

    artifacts.add(artifact)

    return artifact
  }

  override fun removeArtifact(artifact: Artifact) {
    artifacts.remove(artifact)
  }

  override fun getOrCreateModifiableArtifact(artifact: Artifact): ModifiableArtifact {
    return artifact as ModifiableArtifact
  }

  override fun getModifiableCopy(artifact: Artifact): Artifact {
    return artifact
  }

  override fun addListener(listener: ArtifactListener) {
  }

  override fun removeListener(listener: ArtifactListener) {
  }

  override fun isModified(): Boolean {
    return true
  }

  // TODO: Could we apply the changes to the storage directly?
  // This way we could probably replace this old-style modifiable model API with a couple of utility methods
  // and don't overload MavenWorkspaceConfigurator with artifact-specific knowledge.
  // (And also avoid keeping intermediate data in memory)
  fun applyToStorage() {
    for (artifact in artifacts) {
      val source = LegacyBridgeJpsEntitySourceFactory.createEntitySourceForArtifact(project, artifact.externalSource)
      val rootElement = artifact.rootElement
      val rootElementEntity = rootElement.getOrAddEntity(storage, source, project) as CompositePackagingElementEntity

      val artifactEntity = storage addEntity ArtifactEntity(artifact.name, artifact.artifactType.id, false, source) {
        this.outputUrl = artifact.getOutputUrl()
        this.rootElement = rootElementEntity
        this.includeInProjectBuild = artifact.isBuildOnMake
      }

      for (provider in artifact.propertiesProviders) {
        val properties = artifact.getProperties(provider)

        if (properties == null) {
          val (toBeRemoved, filtered) = artifactEntity.customProperties.partition { it.providerType == provider.id }
          if (toBeRemoved.isNotEmpty()) {
            storage.modifyEntity(artifactEntity) {
              this.customProperties = filtered
            }
            toBeRemoved.forEach { storage.removeEntity(it) }
          }
        }
        else {
          val tag = properties.propertiesTag()

          val existingProperty = artifactEntity.customProperties.find { it.providerType == provider.id }

          if (existingProperty == null) {
            storage addEntity ArtifactPropertiesEntity(provider.id, artifactEntity.entitySource) {
              this.artifact = artifactEntity
              this.propertiesXmlTag = tag
            }
          }
          else {
            storage.modifyEntity(existingProperty) {
              this.propertiesXmlTag = tag
            }
          }
        }
      }
    }
  }

  private fun ArtifactProperties<*>.propertiesTag(): String? {
    val state = state
    return if (state != null) {
      val element = serialize(state) ?: return null
      element.name = "options"
      JDOMUtil.write(element)
    }
    else null
  }

  override fun commit() {
  }

  override fun dispose() {
  }

}
