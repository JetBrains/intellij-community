// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.tests.fe10

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.caches.trackers.KotlinIDEModificationTrackerService
import org.jetbrains.kotlin.idea.project.test.base.FrontendConfiguration

object FE10FrontedConfiguration : FrontendConfiguration() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.FE10_PLUGIN

    override fun invalidateCaches(project: Project) {
        KotlinIDEModificationTrackerService.invalidateCaches(project)
        JavaLibraryModificationTracker.incModificationCount(project)
    }
}