// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.test

import com.intellij.configurationStore.TestScheme
import com.intellij.configurationStore.TestSchemeProcessor
import com.intellij.configurationStore.schemeManager.SchemeManagerImpl
import com.intellij.testFramework.ProjectRule
import org.junit.ClassRule

abstract class LoadTestBase: IcsTestCase() {
  companion object {
    const val dirName = "keymaps"

    @JvmField @ClassRule val projectRule = ProjectRule(runPostStartUpActivities = false)
  }

  protected fun createSchemeManager(dirPath: String): SchemeManagerImpl<TestScheme, TestScheme> {
    @Suppress("UNCHECKED_CAST")
    return icsManager.schemeManagerFactory.value.create(dirPath, TestSchemeProcessor(), streamProvider = provider) as SchemeManagerImpl<TestScheme, TestScheme>
  }
}