// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test

import com.intellij.testFramework.LightPlatformCodeInsightTestCase

abstract class KotlinLightPlatformCodeInsightTestCase : LightPlatformCodeInsightTestCase(),
                                                        ExpectedPluginModeProvider {

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
        enableKotlinOfficialCodeStyle(project)
    }

    override fun tearDown() {
        runAll(
            { disableKotlinOfficialCodeStyle(project) },
            { super.tearDown() },
        )
    }
}