// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.MutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class MavenProjectsTreeSettingsEntityImpl(val dataSource: MavenProjectsTreeSettingsEntityData) : MavenProjectsTreeSettingsEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  override val ignoredFilePaths: List<String>
    get() = dataSource.ignoredFilePaths

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
      if (!getEntityData().isIgnoredFilePathsInitialized()) {
        error("Field MavenProjectsTreeSettingsEntity#ignoredFilePaths should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_ignoredFilePaths = getEntityData().ignoredFilePaths
      if (collection_ignoredFilePaths is MutableWorkspaceList<*>) {
        collection_ignoredFilePaths.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as MavenProjectsTreeSettingsEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.ignoredFilePaths != dataSource.ignoredFilePaths) this.ignoredFilePaths = dataSource.ignoredFilePaths.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    private val ignoredFilePathsUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("ignoredFilePaths")
    }
    override var ignoredFilePaths: MutableList<String>
      get() {
        val collection_ignoredFilePaths = getEntityData().ignoredFilePaths
        if (collection_ignoredFilePaths !is MutableWorkspaceList) return collection_ignoredFilePaths
        if (diff == null || modifiable.get()) {
          collection_ignoredFilePaths.setModificationUpdateAction(ignoredFilePathsUpdater)
        }
        else {
          collection_ignoredFilePaths.cleanModificationUpdateAction()
        }
        return collection_ignoredFilePaths
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).ignoredFilePaths = value
        ignoredFilePathsUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<MavenProjectsTreeSettingsEntity> = MavenProjectsTreeSettingsEntity::class.java
  }
}

class MavenProjectsTreeSettingsEntityData : WorkspaceEntityData<MavenProjectsTreeSettingsEntity>() {
  lateinit var ignoredFilePaths: MutableList<String>

  fun isIgnoredFilePathsInitialized(): Boolean = ::ignoredFilePaths.isInitialized

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
    clonedEntity.ignoredFilePaths = clonedEntity.ignoredFilePaths.toMutableWorkspaceList()
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
    return MavenProjectsTreeSettingsEntity(ignoredFilePaths, entitySource) {
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
    if (this.ignoredFilePaths != other.ignoredFilePaths) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as MavenProjectsTreeSettingsEntityData

    if (this.ignoredFilePaths != other.ignoredFilePaths) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + ignoredFilePaths.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + ignoredFilePaths.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    this.ignoredFilePaths?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
