// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.UsedClassesCollector
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(2)
open class MavenCustomModuleNameMappingEntityImpl(val dataSource: MavenCustomModuleNameMappingEntityData) : MavenCustomModuleNameMappingEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  override val customModuleNames: Map<VirtualFileUrl, String>
    get() = dataSource.customModuleNames

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: MavenCustomModuleNameMappingEntityData?) : ModifiableWorkspaceEntityBase<MavenCustomModuleNameMappingEntity, MavenCustomModuleNameMappingEntityData>(
    result), MavenCustomModuleNameMappingEntity.Builder {
    constructor() : this(MavenCustomModuleNameMappingEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity MavenCustomModuleNameMappingEntity is already created in a different builder")
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
      if (!getEntityData().isCustomModuleNamesInitialized()) {
        error("Field MavenCustomModuleNameMappingEntity#customModuleNames should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as MavenCustomModuleNameMappingEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.customModuleNames != dataSource.customModuleNames) this.customModuleNames = dataSource.customModuleNames.toMutableMap()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var customModuleNames: Map<VirtualFileUrl, String>
      get() = getEntityData().customModuleNames
      set(value) {
        checkModificationAllowed()
        getEntityData(true).customModuleNames = value
        changedProperty.add("customModuleNames")
      }

    override fun getEntityClass(): Class<MavenCustomModuleNameMappingEntity> = MavenCustomModuleNameMappingEntity::class.java
  }
}

class MavenCustomModuleNameMappingEntityData : WorkspaceEntityData<MavenCustomModuleNameMappingEntity>() {
  lateinit var customModuleNames: Map<VirtualFileUrl, String>

  fun isCustomModuleNamesInitialized(): Boolean = ::customModuleNames.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<MavenCustomModuleNameMappingEntity> {
    val modifiable = MavenCustomModuleNameMappingEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): MavenCustomModuleNameMappingEntity {
    return getCached(snapshot) {
      val entity = MavenCustomModuleNameMappingEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return MavenCustomModuleNameMappingEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return MavenCustomModuleNameMappingEntity(customModuleNames, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as MavenCustomModuleNameMappingEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.customModuleNames != other.customModuleNames) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as MavenCustomModuleNameMappingEntityData

    if (this.customModuleNames != other.customModuleNames) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + customModuleNames.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + customModuleNames.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    this.customModuleNames?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
