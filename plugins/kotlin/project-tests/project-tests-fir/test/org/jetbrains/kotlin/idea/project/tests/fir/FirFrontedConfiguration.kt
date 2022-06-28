// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.tests.fir

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.analysis.api.InvalidWayOfUsingAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtAnalysisSessionProvider
import org.jetbrains.kotlin.analysis.api.fir.KtFirAnalysisSessionProvider
import org.jetbrains.kotlin.analysis.providers.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.analyzer.KotlinModificationTrackerService
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.idea.caches.trackers.KotlinIDEModificationTrackerService
import org.jetbrains.kotlin.idea.project.test.base.FrontendConfiguration

object FirFrontedConfiguration: FrontendConfiguration() {
    override val pluginKind: KotlinPluginKind get() = KotlinPluginKind.FIR_PLUGIN

    @OptIn(InvalidWayOfUsingAnalysisSession::class)
    override fun invalidateCaches(project: Project) {
        KotlinIDEModificationTrackerService.invalidateCaches(project)
        LibraryModificationTracker.getInstance(project).incModificationCount()
        KtAnalysisSessionProvider.getInstance(project).clearCaches()
    }
}