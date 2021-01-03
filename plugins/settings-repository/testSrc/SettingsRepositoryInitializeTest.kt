// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.test

import com.intellij.configurationStore.TestScheme
import com.intellij.configurationStore.serialize
import com.intellij.util.toByteArray
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.settingsRepository.SyncType
import org.jetbrains.settingsRepository.doSync
import org.jetbrains.settingsRepository.git.commit
import org.junit.Test

class SettingsRepositoryInitializeTest : LoadTestBase() {

  override fun createAndActivateRepository(): Boolean {
    return false
  }

  @Test fun `scheme is loaded on settings repository initialization`() {
    runBlocking {
      val remoteScheme = TestScheme("remote")
      val remoteRepository = tempDirManager.createRepository()
      remoteRepository
        .add("$dirName/CustomKeymap.xml", serialize(remoteScheme)!!.toByteArray())
        .commit("add")
      val schemeManager = createSchemeManager(dirName)

      doSync(icsManager, project = null, syncType = SyncType.OVERWRITE_LOCAL, url = remoteRepository.directory.path)

      assertThat(schemeManager.allSchemes).containsOnly(remoteScheme)
    }
  }

  @Test fun `scheme is reloaded in case of merge`() {
    runBlocking {
      val remoteScheme = TestScheme("remote")
      val remoteRepository = tempDirManager.createRepository()
      remoteRepository
        .add("$dirName/CustomKeymap.xml", serialize(remoteScheme)!!.toByteArray())
        .commit("add")
      val schemeManager = createSchemeManager(dirName)

      doSync(icsManager, project = null, syncType = SyncType.MERGE, url = remoteRepository.directory.path)

      assertThat(schemeManager.allSchemes).containsOnly(remoteScheme)
    }
  }
}