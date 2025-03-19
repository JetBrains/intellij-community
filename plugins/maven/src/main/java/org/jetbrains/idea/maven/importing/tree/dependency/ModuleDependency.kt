// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.dependency

import com.intellij.openapi.roots.DependencyScope

class ModuleDependency(moduleName: String, scope: DependencyScope, val isTestJar: Boolean) : MavenImportDependency<String>(moduleName, scope)
