package com.intellij.python.pyproject.model.internal.impl

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToOneParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.python.common.tools.ToolId
import com.intellij.python.pyproject.model.internal.PyProjectTomlWorkspaceEntity
import com.intellij.python.pyproject.model.internal.PyProjectTomlWorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class PyProjectTomlWorkspaceEntityImpl(private val dataSource: PyProjectTomlWorkspaceEntityData) : PyProjectTomlWorkspaceEntity, WorkspaceEntityBase(
  dataSource) {

  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java,
                                                                          PyProjectTomlWorkspaceEntity::class.java,
                                                                          ConnectionId.ConnectionType.ONE_TO_ONE, false)

    private val connections = listOf<ConnectionId>(
      MODULE_CONNECTION_ID,
    )

  }

  override val participatedTools: Map<ToolId, ModuleId?>
    get() {
      readField("participatedTools")
      return dataSource.participatedTools
    }
  override val dirWithToml: VirtualFileUrl
    get() {
      readField("dirWithToml")
      return dataSource.dirWithToml
    }

  override val module: ModuleEntity
    get() = snapshot.extractOneToOneParent(MODULE_CONNECTION_ID, this)!!

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: PyProjectTomlWorkspaceEntityData?) : ModifiableWorkspaceEntityBase<PyProjectTomlWorkspaceEntity, PyProjectTomlWorkspaceEntityData>(
    result), PyProjectTomlWorkspaceEntityBuilder {
    internal constructor() : this(PyProjectTomlWorkspaceEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity PyProjectTomlWorkspaceEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "dirWithToml", this.dirWithToml)
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isParticipatedToolsInitialized()) {
        error("Field PyProjectTomlWorkspaceEntity#participatedTools should be initialized")
      }
      if (!getEntityData().isDirWithTomlInitialized()) {
        error("Field PyProjectTomlWorkspaceEntity#dirWithToml should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToOneParent<WorkspaceEntityBase>(MODULE_CONNECTION_ID, this) == null) {
          error("Field PyProjectTomlWorkspaceEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field PyProjectTomlWorkspaceEntity#module should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as PyProjectTomlWorkspaceEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.participatedTools != dataSource.participatedTools) this.participatedTools = dataSource.participatedTools.toMutableMap()
      if (this.dirWithToml != dataSource.dirWithToml) this.dirWithToml = dataSource.dirWithToml
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var participatedTools: Map<ToolId, ModuleId?>
      get() = getEntityData().participatedTools
      set(value) {
        checkModificationAllowed()
        getEntityData(true).participatedTools = value
        changedProperty.add("participatedTools")
      }

    override var dirWithToml: VirtualFileUrl
      get() = getEntityData().dirWithToml
      set(value) {
        checkModificationAllowed()
        getEntityData(true).dirWithToml = value
        changedProperty.add("dirWithToml")
        val _diff = diff
        if (_diff != null) index(this, "dirWithToml", value)
      }

    override var module: ModuleEntityBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(MODULE_CONNECTION_ID, this) as? ModuleEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntityBuilder)
        }
        else {
          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntityBuilder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(MODULE_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
        }
        changedProperty.add("module")
      }

    override fun getEntityClass(): Class<PyProjectTomlWorkspaceEntity> = PyProjectTomlWorkspaceEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class PyProjectTomlWorkspaceEntityData : WorkspaceEntityData<PyProjectTomlWorkspaceEntity>() {
  lateinit var participatedTools: Map<ToolId, ModuleId?>
  lateinit var dirWithToml: VirtualFileUrl

  internal fun isParticipatedToolsInitialized(): Boolean = ::participatedTools.isInitialized
  internal fun isDirWithTomlInitialized(): Boolean = ::dirWithToml.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<PyProjectTomlWorkspaceEntity> {
    val modifiable = PyProjectTomlWorkspaceEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): PyProjectTomlWorkspaceEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = PyProjectTomlWorkspaceEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.python.pyproject.model.internal.PyProjectTomlWorkspaceEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return PyProjectTomlWorkspaceEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return PyProjectTomlWorkspaceEntity(participatedTools, dirWithToml, entitySource) {
      parents.filterIsInstance<ModuleEntityBuilder>().singleOrNull()?.let { this.module = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ModuleEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as PyProjectTomlWorkspaceEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.participatedTools != other.participatedTools) return false
    if (this.dirWithToml != other.dirWithToml) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as PyProjectTomlWorkspaceEntityData

    if (this.participatedTools != other.participatedTools) return false
    if (this.dirWithToml != other.dirWithToml) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + participatedTools.hashCode()
    result = 31 * result + dirWithToml.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + participatedTools.hashCode()
    result = 31 * result + dirWithToml.hashCode()
    return result
  }
}
