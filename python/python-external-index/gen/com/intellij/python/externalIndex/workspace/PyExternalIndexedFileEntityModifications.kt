@file:JvmName("PyExternalIndexedFileEntityModifications")

package com.intellij.python.externalIndex.workspace

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
internal interface PyExternalIndexedFileEntityBuilder : WorkspaceEntityBuilder<PyExternalIndexedFileEntity> {
  override var entitySource: EntitySource
  var file: VirtualFileUrl
}

internal object PyExternalIndexedFileEntityType : EntityType<PyExternalIndexedFileEntity, PyExternalIndexedFileEntityBuilder>() {
  override val entityClass: Class<PyExternalIndexedFileEntity> get() = PyExternalIndexedFileEntity::class.java
  operator fun invoke(
    file: VirtualFileUrl,
    entitySource: EntitySource,
    init: (PyExternalIndexedFileEntityBuilder.() -> Unit)? = null,
  ): PyExternalIndexedFileEntityBuilder {
    val builder = builder()
    builder.file = file
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

internal fun MutableEntityStorage.modifyPyExternalIndexedFileEntity(
  entity: PyExternalIndexedFileEntity,
  modification: PyExternalIndexedFileEntityBuilder.() -> Unit,
): PyExternalIndexedFileEntity = modifyEntity(PyExternalIndexedFileEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createPyExternalIndexedFileEntity")
internal fun PyExternalIndexedFileEntity(
  file: VirtualFileUrl,
  entitySource: EntitySource,
  init: (PyExternalIndexedFileEntityBuilder.() -> Unit)? = null,
): PyExternalIndexedFileEntityBuilder = PyExternalIndexedFileEntityType(file, entitySource, init)
