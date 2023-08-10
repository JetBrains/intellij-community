// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.util.ThrowableRunnable

abstract class KotlinLightPlatformCodeInsightTestCase : LightPlatformCodeInsightTestCase() {

    override fun setUp() {
        super.setUp()
        enableKotlinOfficialCodeStyle(project)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { disableKotlinOfficialCodeStyle(project) },
            ThrowableRunnable { super.tearDown() },
        )
    }
}