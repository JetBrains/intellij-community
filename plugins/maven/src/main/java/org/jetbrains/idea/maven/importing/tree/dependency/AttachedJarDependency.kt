// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.dependency

import com.intellij.openapi.roots.DependencyScope

class AttachedJarDependency(artifactName: String,
                            val classes: List<String>,
                            val sources: List<String>,
                            val javadocs: List<String>,
                            scope: DependencyScope) : MavenImportDependency<String>(artifactName, scope)