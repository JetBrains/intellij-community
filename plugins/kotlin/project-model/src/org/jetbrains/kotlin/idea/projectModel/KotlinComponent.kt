// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.Serializable

typealias KotlinDependencyId = Long

@Deprecated(level = DeprecationLevel.ERROR, message = "Use org.jetbrains.kotlin.gradle.KotlinComponent instead")
interface KotlinModule : Serializable {
    val name: String
    val dependencies: Array<KotlinDependencyId>

    @Deprecated(level = DeprecationLevel.WARNING, message = "Use org.jetbrains.kotlin.gradle.KotlinComponent#isTestComponent instead")
    val isTestModule: Boolean
}

@Suppress("DEPRECATION_ERROR")
interface KotlinComponent : KotlinModule {
    val isTestComponent: Boolean

    @Deprecated(
        "Use org.jetbrains.kotlin.gradle.KotlinComponent#isTestComponent instead", level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("isTestComponent")
    )
    @Suppress("OverridingDeprecatedMember")
    override val isTestModule: Boolean
        get() = isTestComponent
}
