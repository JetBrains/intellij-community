// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

internal fun switchToBootstrapKotlinCompiler(preferences: GeneratorPreferences) {
    val ticket = preferences.getTicketAndCheck()

    println("Switching Kotlin compiler version to $BOOTSTRAP_VERSION...")

    updateProjectAndCommit(
        preferences = preferences,
        modifications = listOf(
            preferences::kotlincArtifactsMode.modify(GeneratorPreferences.ArtifactMode.BOOTSTRAP),
        ),
        commitTitle = "[kotlin] $ticket restore cooperative development",
    )
}
