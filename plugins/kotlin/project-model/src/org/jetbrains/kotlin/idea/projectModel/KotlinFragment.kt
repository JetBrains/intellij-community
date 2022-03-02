// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.File
import java.io.Serializable

interface KotlinFragmentResolvedDependency : Serializable {
    val dependencyIdentifier: String
}

interface KotlinFragment : Serializable {
    val fragmentName: String
    val isTestFragment: Boolean
    val moduleIdentifier: KotlinModuleIdentifier
    val languageSettings: KotlinLanguageSettings?
    val directRefinesFragments: Collection<KotlinFragment>
    val resolvedDependencies: Collection<KotlinFragmentResolvedDependency>
    val sourceDirs: Set<File>
    val resourceDirs: Set<File>
}
