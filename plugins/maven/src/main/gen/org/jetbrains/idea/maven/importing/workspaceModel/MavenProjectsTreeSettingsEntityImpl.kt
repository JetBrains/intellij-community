// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.UsedClassesCollector
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(2)
open class MavenProjectsTreeSettingsEntityImpl(val dataSource: MavenProjectsTreeSettingsEntityData) : MavenProjectsTreeSettingsEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  override val importedFilePaths: List<String>
    get() = dataSource.importedFilePaths

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: MavenProjectsTreeSettingsEntityData?) : ModifiableWorkspaceEntityBase<MavenProjectsTreeSettingsEntity, MavenProjectsTreeSettingsEntityData>(
    result), MavenProjectsTreeSettingsEntity.Builder {
    constructor() : this(MavenProjectsTreeSettingsEntityData())

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
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
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

class MavenProjectsTreeSettingsEntityData : WorkspaceEntityData<MavenProjectsTreeSettingsEntity>() {
  lateinit var importedFilePaths: MutableList<String>

  fun isImportedFilePathsInitialized(): Boolean = ::importedFilePaths.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<MavenProjectsTreeSettingsEntity> {
    val modifiable = MavenProjectsTreeSettingsEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): MavenProjectsTreeSettingsEntity {
    return getCached(snapshot) {
      val entity = MavenProjectsTreeSettingsEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
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

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
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

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    this.importedFilePaths?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
