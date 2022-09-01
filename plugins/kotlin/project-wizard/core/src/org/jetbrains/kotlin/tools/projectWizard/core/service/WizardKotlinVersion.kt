// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.core.service

import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Repository
import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version

data class WizardKotlinVersion(
    val version: Version,
    val kind: KotlinVersionKind,
    val repositories: List<Repository>,
    val buildSystemPluginRepository: (BuildSystemType) -> List<Repository>,
) {
    constructor(
        version: Version,
        kind: KotlinVersionKind,
        repository: Repository,
        buildSystemPluginRepository: (BuildSystemType) -> List<Repository>,
        unused:Any? = null, // hack to save binary compatibility with the mobile-ide
    ) : this(
        version,
        kind,
        listOf(repository),
        buildSystemPluginRepository
    )

    @Deprecated(message = "This declaration is used in mobile-ide 213 platform, left for compatibility")
    constructor(
        version: Version,
        kind: KotlinVersionKind,
        repository: Repository,
        buildSystemPluginRepository: (BuildSystemType) -> Repository?
    ): this(
        version,
        kind, listOf(repository),
        { listOfNotNull(buildSystemPluginRepository(it)) }
    )
}