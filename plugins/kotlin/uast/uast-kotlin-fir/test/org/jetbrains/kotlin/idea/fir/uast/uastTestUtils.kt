// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.uast

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.providers.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.analysis.api.InvalidWayOfUsingAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtAnalysisSessionProvider

@OptIn(InvalidWayOfUsingAnalysisSession::class)
internal fun Project.invalidateAllCachesForUastTests() {
    service<KotlinModificationTrackerFactory>().incrementModificationsCount()
    service<KtAnalysisSessionProvider>().clearCaches()
}