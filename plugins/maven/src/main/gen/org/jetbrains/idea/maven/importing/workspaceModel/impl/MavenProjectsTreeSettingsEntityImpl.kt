// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.idea.maven.importing.workspaceModel.MavenProjectsTreeSettingsEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class MavenProjectsTreeSettingsEntityImpl(private val dataSource: MavenProjectsTreeSettingsEntityData) :
  MavenProjectsTreeSettingsEntity, WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val importedFilePaths: List<String>
    get() {
      readField("importedFilePaths")
      return dataSource.importedFilePaths
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: MavenProjectsTreeSettingsEntityData?) :
    ModifiableWorkspaceEntityBase<MavenProjectsTreeSettingsEntity, MavenProjectsTreeSettingsEntityData>(result),
    MavenProjectsTreeSettingsEntity.Builder {
    internal constructor() : this(MavenProjectsTreeSettingsEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity MavenProjectsTreeSettingsEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isImportedFilePathsInitialized()) {
        error("Field MavenProjectsTreeSettingsEntity#importedFilePaths should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_importedFilePaths = getEntityData().importedFilePaths
      if (collection_importedFilePaths is MutableWorkspaceList<*>) {
        collection_importedFilePaths.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as MavenProjectsTreeSettingsEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.importedFilePaths != dataSource.importedFilePaths) this.importedFilePaths = dataSource.importedFilePaths.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    private val importedFilePathsUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("importedFilePaths")
    }
    override var importedFilePaths: MutableList<String>
      get() {
        val collection_importedFilePaths = getEntityData().importedFilePaths
        if (collection_importedFilePaths !is MutableWorkspaceList) return collection_importedFilePaths
        if (diff == null || modifiable.get()) {
          collection_importedFilePaths.setModificationUpdateAction(importedFilePathsUpdater)
        }
        else {
          collection_importedFilePaths.cleanModificationUpdateAction()
        }
        return collection_importedFilePaths
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).importedFilePaths = value
        importedFilePathsUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<MavenProjectsTreeSettingsEntity> = MavenProjectsTreeSettingsEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class MavenProjectsTreeSettingsEntityData : WorkspaceEntityData<MavenProjectsTreeSettingsEntity>() {
  lateinit var importedFilePaths: MutableList<String>

  internal fun isImportedFilePathsInitialized(): Boolean = ::importedFilePaths.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<MavenProjectsTreeSettingsEntity> {
    val modifiable = MavenProjectsTreeSettingsEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): MavenProjectsTreeSettingsEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = MavenProjectsTreeSettingsEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "org.jetbrains.idea.maven.importing.workspaceModel.MavenProjectsTreeSettingsEntity"
    ) as EntityMetadata
  }

  override fun clone(): MavenProjectsTreeSettingsEntityData {
    val clonedEntity = super.clone()
    clonedEntity as MavenProjectsTreeSettingsEntityData
    clonedEntity.importedFilePaths = clonedEntity.importedFilePaths.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return MavenProjectsTreeSettingsEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return MavenProjectsTreeSettingsEntity(importedFilePaths, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as MavenProjectsTreeSettingsEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.importedFilePaths != other.importedFilePaths) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as MavenProjectsTreeSettingsEntityData

    if (this.importedFilePaths != other.importedFilePaths) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + importedFilePaths.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + importedFilePaths.hashCode()
    return result
  }
}
