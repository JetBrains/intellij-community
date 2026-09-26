// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.python.externalIndex.workspace.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.python.externalIndex.workspace.PyExternalIndexedFileEntity
import com.intellij.python.externalIndex.workspace.PyExternalIndexedFileEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class PyExternalIndexedFileEntityImpl(private val dataSource: PyExternalIndexedFileEntityData) : PyExternalIndexedFileEntity,
                                                                                                          WorkspaceEntityBase(dataSource) {

  override val file: VirtualFileUrl
    get() {
      readField("file")
      return dataSource.file
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: PyExternalIndexedFileEntityData?) :
    ModifiableWorkspaceEntityBase<PyExternalIndexedFileEntity, PyExternalIndexedFileEntityData>(result),
    PyExternalIndexedFileEntityBuilder {
    internal constructor() : this(PyExternalIndexedFileEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isFileInitialized()) {
        error("Field PyExternalIndexedFileEntity#file should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as PyExternalIndexedFileEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.file != dataSource.file) this.file = dataSource.file
      updateChildToParentReferences(parents)
    }

    override fun index() {
      index(this, "file", this.file)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var file: VirtualFileUrl
      get() = getEntityData().file
      set(value) {
        checkModificationAllowed()
        getEntityData(true).file = value
        changedProperty.add("file")
        val _diff = diff
        if (_diff != null) index(this, "file", value)
      }

    override fun getEntityClass(): Class<PyExternalIndexedFileEntity> = PyExternalIndexedFileEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class PyExternalIndexedFileEntityData : WorkspaceEntityData<PyExternalIndexedFileEntity>() {
  lateinit var file: VirtualFileUrl
  internal fun isFileInitialized(): Boolean = ::file.isInitialized
  override fun newInstance(): PyExternalIndexedFileEntity = PyExternalIndexedFileEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<PyExternalIndexedFileEntity, *> =
    PyExternalIndexedFileEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.python.externalIndex.workspace.PyExternalIndexedFileEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return PyExternalIndexedFileEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return PyExternalIndexedFileEntity(file, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as PyExternalIndexedFileEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.file != other.file) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as PyExternalIndexedFileEntityData
    if (this.file != other.file) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + file.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + file.hashCode()
    return result
  }
}
