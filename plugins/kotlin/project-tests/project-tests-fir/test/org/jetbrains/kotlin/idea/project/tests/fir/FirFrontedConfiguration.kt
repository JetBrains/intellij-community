// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.tests.fir

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisApiInternals
import org.jetbrains.kotlin.analysis.api.session.KtAnalysisSessionProvider
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.caches.trackers.KotlinIDEModificationTrackerService
import org.jetbrains.kotlin.idea.project.test.base.FrontendConfiguration

object FirFrontedConfiguration: FrontendConfiguration() {
    override val pluginKind: KotlinPluginKind get() = KotlinPluginKind.FIR_PLUGIN

    @OptIn(KtAnalysisApiInternals::class)
    override fun invalidateCaches(project: Project) {
        KotlinIDEModificationTrackerService.invalidateCaches(project)
        JavaLibraryModificationTracker.incModificationCount(project)
        KtAnalysisSessionProvider.getInstance(project).clearCaches()
    }
}