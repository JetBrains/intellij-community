// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.project.tests

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.analysis.providers.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.idea.base.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.assertKotlinPluginKind
import org.jetbrains.kotlin.idea.base.project.test.AbstractProjectBasedTest

abstract class AbstractFirProjectBasedTests: AbstractProjectBasedTest() {
    override fun setUp() {
        super.setUp()
        assertKotlinPluginKind(KotlinPluginKind.FIR_PLUGIN)
    }

    override fun invalidateCaches(project: Project) {
        PsiManager.getInstance(project).dropResolveCaches()
        project.getService(KotlinModificationTrackerFactory::class.java).incrementModificationsCount()
    }
}