// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.testFramework.TestApplicationManager
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Pins the registration order of the core action XMLs.
 * The first registered action wins keymap dispatch, so this order is load-bearing.
 */
class CoreActionRegistrationOrderTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun initializeApplication() {
      // This module doesn't depend on intellij.platform.testFramework.junit5,
      // so @TestApplication isn't available on this classpath.
      TestApplicationManager.getInstance()
    }
  }

  @Test
  fun coreActionFilesRegisterInDocumentOrder() {
    // PriorityEditorLangActions.xml < PlatformActions.xml < ExecutionActions.xml < LangActions.xml
    assertRegisteredBefore("SelectVirtualTemplateElement", "Other.KeymapGroup")
    assertRegisteredBefore("EditorChooseLookupItem", "Other.KeymapGroup")
    assertRegisteredBefore("Other.KeymapGroup", "RunToolbarActionsGroup")
    assertRegisteredBefore("RunToolbarActionsGroup", "LangCodeInsightActions")
  }

  @Test
  fun priorityEditorActionsRegisterBeforeTheirKeymapRivals() {
    assertRegisteredBefore("EditorChooseLookupItem", "EditorEnter")
    assertRegisteredBefore("ExpandLiveTemplateByTab", "EditorTab")
    assertRegisteredBefore("Generate", "NewElement")
  }

  private fun assertRegisteredBefore(earlierId: String, laterId: String) {
    val actionManager = ActionManagerEx.getInstanceEx()
    listOf(earlierId, laterId).forEach { id ->
      assertNotNull(actionManager.getActionOrStub(id)) { "The action '$id' is not registered." }
    }
    assertTrue(actionManager.registrationOrderComparator.compare(earlierId, laterId) < 0) {
      "Registration order regressed. The action '$earlierId' must register before the action '$laterId'."
    }
  }
}
