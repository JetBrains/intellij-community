// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WizardVersionTest : BasePlatformTestCase() {
    fun testDefaultVersionsExist() {
        assertNotNull("Wizard version default data should exist", KotlinWizardVersionStore.getInstance().state)
    }
}