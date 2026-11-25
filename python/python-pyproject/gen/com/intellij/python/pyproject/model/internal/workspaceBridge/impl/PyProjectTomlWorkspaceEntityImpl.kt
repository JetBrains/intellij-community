@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.python.pyproject.model.internal.workspaceBridge.impl

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.model.internal.workspaceBridge.PyProjectTomlWorkspaceEntity
import com.intellij.python.pyproject.model.internal.workspaceBridge.PyProjectTomlWorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class PyProjectTomlWorkspaceEntityImpl(private val dataSource: PyProjectTomlWorkspaceEntityData) : PyProjectTomlWorkspaceEntity,
                                                                                                            WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleEntity::class.java, PyProjectTomlWorkspaceEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    private val connections = listOf<ConnectionId>(MODULE_CONNECTION_ID)
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
  override val dependencies: List<String>
    get() {
      readField("dependencies")
      return dataSource.dependencies
    }
  override val sourceRoots: List<VirtualFileUrl>
    get() {
      readField("sourceRoots")
      return dataSource.sourceRoots
    }
  override val addedSourceRoots: List<VirtualFileUrl>
    get() {
      readField("addedSourceRoots")
      return dataSource.addedSourceRoots
    }
  override val module: ModuleEntity
    get() = snapshot.instrumentation.getParent(MODULE_CONNECTION_ID, this) as? ModuleEntity
            ?: error("Parent module not found for PyProjectTomlWorkspaceEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: PyProjectTomlWorkspaceEntityData?) :
    ModifiableWorkspaceEntityBase<PyProjectTomlWorkspaceEntity, PyProjectTomlWorkspaceEntityData>(result),
    PyProjectTomlWorkspaceEntityBuilder {
    internal constructor() : this(PyProjectTomlWorkspaceEntityData())

    override fun checkInitialization() {
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
      if (!getEntityData().isDependenciesInitialized()) {
        error("Field PyProjectTomlWorkspaceEntity#dependencies should be initialized")
      }
      if (!getEntityData().isSourceRootsInitialized()) {
        error("Field PyProjectTomlWorkspaceEntity#sourceRoots should be initialized")
      }
      if (!getEntityData().isAddedSourceRootsInitialized()) {
        error("Field PyProjectTomlWorkspaceEntity#addedSourceRoots should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(MODULE_CONNECTION_ID, this) == null) {
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

    override fun afterModification() {
      val collection_dependencies = getEntityData().dependencies
      if (collection_dependencies is MutableWorkspaceList<*>) {
        collection_dependencies.cleanModificationUpdateAction()
      }
      val collection_sourceRoots = getEntityData().sourceRoots
      if (collection_sourceRoots is MutableWorkspaceList<*>) {
        collection_sourceRoots.cleanModificationUpdateAction()
      }
      val collection_addedSourceRoots = getEntityData().addedSourceRoots
      if (collection_addedSourceRoots is MutableWorkspaceList<*>) {
        collection_addedSourceRoots.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as PyProjectTomlWorkspaceEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.participatedTools != dataSource.participatedTools) this.participatedTools = dataSource.participatedTools.toMutableMap()
      if (this.dirWithToml != dataSource.dirWithToml) this.dirWithToml = dataSource.dirWithToml
      if (this.dependencies != dataSource.dependencies) this.dependencies = dataSource.dependencies.toMutableList()
      if (this.sourceRoots != dataSource.sourceRoots) this.sourceRoots = dataSource.sourceRoots.toMutableList()
      if (this.addedSourceRoots != dataSource.addedSourceRoots) this.addedSourceRoots = dataSource.addedSourceRoots.toMutableList()
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
      }
    private val dependenciesUpdater: (value: List<String>) -> Unit = { value ->
      changedProperty.add("dependencies")
    }
    override var dependencies: MutableList<String>
      get() {
        val collection_dependencies = getEntityData().dependencies
        if (collection_dependencies !is MutableWorkspaceList) return collection_dependencies
        if (diff == null || modifiable.get()) {
          collection_dependencies.setModificationUpdateAction(dependenciesUpdater)
        }
        else {
          collection_dependencies.cleanModificationUpdateAction()
        }
        return collection_dependencies
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).dependencies = value
        dependenciesUpdater.invoke(value)
      }
    private val sourceRootsUpdater: (value: List<VirtualFileUrl>) -> Unit = { value ->
      changedProperty.add("sourceRoots")
    }
    override var sourceRoots: MutableList<VirtualFileUrl>
      get() {
        val collection_sourceRoots = getEntityData().sourceRoots
        if (collection_sourceRoots !is MutableWorkspaceList) return collection_sourceRoots
        if (diff == null || modifiable.get()) {
          collection_sourceRoots.setModificationUpdateAction(sourceRootsUpdater)
        }
        else {
          collection_sourceRoots.cleanModificationUpdateAction()
        }
        return collection_sourceRoots
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).sourceRoots = value
        sourceRootsUpdater.invoke(value)
      }
    private val addedSourceRootsUpdater: (value: List<VirtualFileUrl>) -> Unit = { value ->
      changedProperty.add("addedSourceRoots")
    }
    override var addedSourceRoots: MutableList<VirtualFileUrl>
      get() {
        val collection_addedSourceRoots = getEntityData().addedSourceRoots
        if (collection_addedSourceRoots !is MutableWorkspaceList) return collection_addedSourceRoots
        if (diff == null || modifiable.get()) {
          collection_addedSourceRoots.setModificationUpdateAction(addedSourceRootsUpdater)
        }
        else {
          collection_addedSourceRoots.cleanModificationUpdateAction()
        }
        return collection_addedSourceRoots
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).addedSourceRoots = value
        addedSourceRootsUpdater.invoke(value)
      }
    override var module: ModuleEntityBuilder
      get() = getParent(MODULE_CONNECTION_ID) as? ModuleEntityBuilder ?: error("module is null for PyProjectTomlWorkspaceEntity")
      set(value) {
        changeParent(value, MODULE_CONNECTION_ID)
        changedProperty.add("module")
      }

    override fun getEntityClass(): Class<PyProjectTomlWorkspaceEntity> = PyProjectTomlWorkspaceEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class PyProjectTomlWorkspaceEntityData : WorkspaceEntityData<PyProjectTomlWorkspaceEntity>() {
  lateinit var participatedTools: Map<ToolId, ModuleId?>
  lateinit var dirWithToml: VirtualFileUrl
  lateinit var dependencies: MutableList<String>
  lateinit var sourceRoots: MutableList<VirtualFileUrl>
  lateinit var addedSourceRoots: MutableList<VirtualFileUrl>
  internal fun isParticipatedToolsInitialized(): Boolean = ::participatedTools.isInitialized
  internal fun isDirWithTomlInitialized(): Boolean = ::dirWithToml.isInitialized
  internal fun isDependenciesInitialized(): Boolean = ::dependencies.isInitialized
  internal fun isSourceRootsInitialized(): Boolean = ::sourceRoots.isInitialized
  internal fun isAddedSourceRootsInitialized(): Boolean = ::addedSourceRoots.isInitialized
  override fun newInstance(): PyProjectTomlWorkspaceEntity = PyProjectTomlWorkspaceEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<PyProjectTomlWorkspaceEntity, *> =
    PyProjectTomlWorkspaceEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.python.pyproject.model.internal.workspaceBridge.PyProjectTomlWorkspaceEntity") as EntityMetadata
  }

  override fun clone(): PyProjectTomlWorkspaceEntityData {
    val clonedEntity = super.clone()
    clonedEntity as PyProjectTomlWorkspaceEntityData
    clonedEntity.dependencies = clonedEntity.dependencies.toMutableWorkspaceList()
    clonedEntity.sourceRoots = clonedEntity.sourceRoots.toMutableWorkspaceList()
    clonedEntity.addedSourceRoots = clonedEntity.addedSourceRoots.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return PyProjectTomlWorkspaceEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return PyProjectTomlWorkspaceEntity(participatedTools, dirWithToml, dependencies, sourceRoots, addedSourceRoots, entitySource) {
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
    if (this.dependencies != other.dependencies) return false
    if (this.sourceRoots != other.sourceRoots) return false
    if (this.addedSourceRoots != other.addedSourceRoots) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as PyProjectTomlWorkspaceEntityData
    if (this.participatedTools != other.participatedTools) return false
    if (this.dirWithToml != other.dirWithToml) return false
    if (this.dependencies != other.dependencies) return false
    if (this.sourceRoots != other.sourceRoots) return false
    if (this.addedSourceRoots != other.addedSourceRoots) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + participatedTools.hashCode()
    result = 31 * result + dirWithToml.hashCode()
    result = 31 * result + dependencies.hashCode()
    result = 31 * result + sourceRoots.hashCode()
    result = 31 * result + addedSourceRoots.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + participatedTools.hashCode()
    result = 31 * result + dirWithToml.hashCode()
    result = 31 * result + dependencies.hashCode()
    result = 31 * result + sourceRoots.hashCode()
    result = 31 * result + addedSourceRoots.hashCode()
    return result
  }
}
