package com.intellij.python.pyproject.model.internal.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.ExtPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl : MetadataStorageBase() {
  override fun initializeMetadata() {
    val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
    val primitiveTypeMapNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Map")

    var typeMetadata: StorageTypeMetadata

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.python.pyproject.model.internal.PyProjectTomlEntitySource",
                                                    properties = listOf(
                                                      OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false,
                                                                          name = "virtualFileUrl",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                            isNullable = false,
                                                                            typeMetadata = FinalClassMetadata.KnownClass(
                                                                              fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false)),
                                                    supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.python.pyproject.model.internal.PyProjectTomlWorkspaceEntity",
                                  entityDataFqName = "com.intellij.python.pyproject.model.internal.impl.PyProjectTomlWorkspaceEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                  fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                            withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "participatedTools",
                            valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                              ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(
                                fqName = "com.intellij.python.common.tools.ToolId", properties = listOf(
                                  OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "id",
                                                      valueType = primitiveTypeStringNotNullable, withDefault = false)),
                                supertypes = listOf())),
                              ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.ClassMetadata(
                                fqName = "com.intellij.platform.workspace.jps.entities.ModuleId", properties = listOf(
                                  OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name",
                                                      valueType = primitiveTypeStringNotNullable, withDefault = false),
                                  OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "presentableName",
                                                      valueType = primitiveTypeStringNotNullable, withDefault = false)),
                                supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId")))),
                                                                            primitive = primitiveTypeMapNotNullable), withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "dirWithToml",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                  fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                            withDefault = false),
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "module",
                            valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                          entityFqName = "com.intellij.platform.workspace.jps.entities.ModuleEntity",
                                                                          isChild = false, isNullable = false), withDefault = false)),
                                  extProperties = listOf(
                                    ExtPropertyMetadata(isComputable = false, isOpen = false, name = "pyProjectTomlEntity",
                                                        receiverFqn = "com.intellij.platform.workspace.jps.entities.ModuleEntity",
                                                        valueType = ValueTypeMetadata.EntityReference(
                                                          connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                          entityFqName = "com.intellij.python.pyproject.model.internal.PyProjectTomlWorkspaceEntity",
                                                          isChild = true, isNullable = true), withDefault = false)), isAbstract = false)

    addMetadata(typeMetadata)
  }

  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "com.intellij.python.pyproject.model.internal.PyProjectTomlWorkspaceEntity", metadataHash = 1371919551)
    addMetadataHash(typeFqn = "com.intellij.python.common.tools.ToolId", metadataHash = -1193602517)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.ModuleId", metadataHash = -575206713)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = -1282078904)
    addMetadataHash(typeFqn = "com.intellij.python.pyproject.model.internal.PyProjectTomlEntitySource", metadataHash = -1054650782)
  }

}
