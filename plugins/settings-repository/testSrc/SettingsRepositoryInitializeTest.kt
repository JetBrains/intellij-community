// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository.test

import com.intellij.configurationStore.TestScheme
import com.intellij.configurationStore.serialize
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.util.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.settingsRepository.SyncType
import org.jetbrains.settingsRepository.doSync
import org.jetbrains.settingsRepository.git.commit
import org.junit.Test

class SettingsRepositoryInitializeTest : LoadTestBase() {
  override fun createAndActivateRepository() = false

  @Test
  fun `scheme is loaded on settings repository initialization`() {
    val remoteScheme = TestScheme("remote")
    val remoteRepository = tempDirManager.createRepository()
    remoteRepository
      .add("$dirName/CustomKeymap.xml", serialize(remoteScheme)!!.toByteArray())
      .commit("add")
    val schemeManager = createSchemeManager(dirName)

    runBlocking {
      // use EDT as production
      withContext(Dispatchers.EDT) {
        doSync(icsManager = icsManager,
               project = null,
               syncType = SyncType.OVERWRITE_LOCAL,
               url = remoteRepository.directory.path,
               owner = ModalTaskOwner.guess())
      }
    }

    assertThat(schemeManager.allSchemes).containsOnly(remoteScheme)
  }

  @Test
  fun `scheme is reloaded in case of merge`() {
    val remoteScheme = TestScheme("remote")
    val remoteRepository = tempDirManager.createRepository()
    remoteRepository
      .add("$dirName/CustomKeymap.xml", serialize(remoteScheme)!!.toByteArray())
      .commit("add")
    val schemeManager = createSchemeManager(dirName)

    runBlocking {
      // use EDT as production
      withContext(Dispatchers.EDT) {
        doSync(icsManager, project = null, syncType = SyncType.MERGE, url = remoteRepository.directory.path, owner = ModalTaskOwner.guess())
      }
    }

    assertThat(schemeManager.allSchemes).containsOnly(remoteScheme)
  }
}