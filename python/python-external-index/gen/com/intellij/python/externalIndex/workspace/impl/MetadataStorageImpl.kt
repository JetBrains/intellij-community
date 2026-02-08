package com.intellij.python.externalIndex.workspace.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl : MetadataStorageBase() {
  override fun initializeMetadata() {

    var typeMetadata: StorageTypeMetadata

    typeMetadata = FinalClassMetadata.ObjectMetadata(
      fqName = "com.intellij.python.externalIndex.workspace.PyExternalIndexedFileEntitySource", properties = listOf(
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                  fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                            withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.python.externalIndex.workspace.PyExternalIndexedFileEntity",
                                  entityDataFqName = "com.intellij.python.externalIndex.workspace.impl.PyExternalIndexedFileEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                  fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                            withDefault = false), OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "file",
                                                                      valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                        isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(
                                                                          fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                      withDefault = false)), extProperties = listOf(), isAbstract = false)

    addMetadata(typeMetadata)
  }

  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "com.intellij.python.externalIndex.workspace.PyExternalIndexedFileEntity", metadataHash = 1049286682)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 1058015871)
    addMetadataHash(typeFqn = "com.intellij.python.externalIndex.workspace.PyExternalIndexedFileEntitySource", metadataHash = -247347603)
  }

}
