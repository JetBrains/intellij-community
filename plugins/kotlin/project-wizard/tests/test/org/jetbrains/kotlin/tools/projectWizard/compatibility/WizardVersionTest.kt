// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase

class WizardVersionTest : BasePlatformTestCase() {
    fun testDefaultVersionsExist() {
        TestCase.assertNotNull("Wizard version default data should exist", KotlinWizardVersionStore.getInstance().state)
    }
}