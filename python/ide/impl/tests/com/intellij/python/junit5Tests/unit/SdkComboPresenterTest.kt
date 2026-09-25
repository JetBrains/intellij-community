// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.SdkComboItem
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.SdkComboPresenter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/**
 * Pure-logic tests for [SdkComboPresenter]. The Swing side effects — hide combo popup, navigate to
 * the "All Interpreters" page, propagate the value to the base combo model — are injected as
 * lambdas; the recorder pins which callback fires for each [SdkComboItem] variant + `null`.
 */
class SdkComboPresenterTest {

  private class Recorder {
    var hideCalls: Int = 0
    var navCalls: Int = 0
    val propagated: MutableList<SdkComboItem?> = mutableListOf()

    fun presenter(): SdkComboPresenter = SdkComboPresenter(
      hidePopup = { hideCalls++ },
      navToAllInterpreters = { navCalls++ },
      propagate = { propagated.add(it) },
    )
  }

  @Test
  fun `ShowAll hides the popup and navigates without propagating`() {
    val r = Recorder()
    r.presenter().onSelectionChanged(SdkComboItem.ShowAll)

    assertEquals(1, r.hideCalls)
    assertEquals(1, r.navCalls)
    assertTrue(r.propagated.isEmpty())
  }

  @Test
  fun `NoInterpreter propagates the value and skips navigation`() {
    val r = Recorder()
    r.presenter().onSelectionChanged(SdkComboItem.NoInterpreter)

    assertEquals(0, r.hideCalls)
    assertEquals(0, r.navCalls)
    assertEquals(listOf<SdkComboItem?>(SdkComboItem.NoInterpreter), r.propagated)
  }

  @Test
  fun `null propagates the value and skips navigation`() {
    val r = Recorder()
    r.presenter().onSelectionChanged(null)

    assertEquals(0, r.hideCalls)
    assertEquals(0, r.navCalls)
    assertEquals(listOf<SdkComboItem?>(null), r.propagated)
  }

  @Test
  fun `Existing propagates the exact item without navigation`() {
    val item: SdkComboItem = SdkComboItem.Existing(stubSdk("Python 3.12"))
    val r = Recorder()
    r.presenter().onSelectionChanged(item)

    assertEquals(0, r.hideCalls)
    assertEquals(0, r.navCalls)
    assertEquals(listOf<SdkComboItem?>(item), r.propagated)
  }

  @Suppress("UNCHECKED_CAST")
  private fun stubSdk(name: String): Sdk =
    Proxy.newProxyInstance(
      Sdk::class.java.classLoader,
      arrayOf(Sdk::class.java),
    ) { _, method, _ -> if (method.name == "getName") name else null } as Sdk
}
