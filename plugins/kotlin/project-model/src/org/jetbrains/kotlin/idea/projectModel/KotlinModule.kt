// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.Serializable

sealed interface KotlinModuleIdentifier : Serializable {
    val moduleClassifier: String?
}

interface KotlinLocalModuleIdentifier : KotlinModuleIdentifier {
    val buildId: String
    val projectId: String
}

interface KotlinMavenModuleIdentifier : KotlinModuleIdentifier {
    val group: String
    val name: String
}

interface KotlinModule : Serializable {
    val moduleIdentifier: KotlinModuleIdentifier
    val fragments: Collection<KotlinFragment>

    companion object {
        const val MAIN_MODULE_NAME = "main"
        const val TEST_MODULE_NAME = "test"
    }
}

val KotlinModule.variants: Collection<KotlinVariant>
    get() = fragments.filterIsInstance<KotlinVariant>()
