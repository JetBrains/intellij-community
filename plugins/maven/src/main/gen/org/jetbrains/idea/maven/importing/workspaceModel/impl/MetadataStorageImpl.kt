// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel.impl

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
    val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
    val primitiveTypeListNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "List")

    var typeMetadata: StorageTypeMetadata

    typeMetadata = FinalClassMetadata.ObjectMetadata(fqName = "org.jetbrains.idea.maven.importing.workspaceModel.MavenEntitySource",
                                                     properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                             isKey = false,
                                                                                             isOpen = false,
                                                                                             name = "virtualFileUrl",
                                                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                               isNullable = true,
                                                                                               typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                 fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                                             withDefault = false)),
                                                     supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "org.jetbrains.idea.maven.importing.workspaceModel.MavenProjectsTreeSettingsEntity",
                                  entityDataFqName = "org.jetbrains.idea.maven.importing.workspaceModel.impl.MavenProjectsTreeSettingsEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "importedFilePaths",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            primitiveTypeStringNotNullable),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)
  }

  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "org.jetbrains.idea.maven.importing.workspaceModel.MavenProjectsTreeSettingsEntity", metadataHash = -21496614)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = -1237028792)
    addMetadataHash(typeFqn = "org.jetbrains.idea.maven.importing.workspaceModel.MavenEntitySource", metadataHash = 582639044)
  }
}
