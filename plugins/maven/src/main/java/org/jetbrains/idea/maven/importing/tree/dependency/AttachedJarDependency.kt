// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.dependency

import com.intellij.openapi.roots.DependencyScope
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryRootTypeId

class AttachedJarDependency(artifactName: String,
                            val rootPaths: List<Pair<String, LibraryRootTypeId>>,
                            scope: DependencyScope) : MavenImportDependency<String>(artifactName, scope)