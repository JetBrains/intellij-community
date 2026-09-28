// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings

import com.intellij.mcpserver.McpServerFreshInstallPolicy
import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Covers the default a product picks on a fresh install, and the migration of an install that predates the consent. */
@TestApplication
internal class McpServerConsentTest {
  @Test
  fun `a fresh install starts the server when the product asks for it`(@TestDisposable disposable: Disposable) {
    installPolicy(disposable, enabled = true)
    val settings = McpServerSettingsImpl()

    settings.noStateLoaded()

    assertThat(settings.enableMcpServer).isTrue()
    assertThat(settings.consent).isEqualTo(McpServerConsent.NOT_ASKED)
    assertThat(settings.enabledByFreshInstallPolicy).isTrue()
  }

  @Test
  fun `a fresh install of a product with no policy leaves the server off`(@TestDisposable disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(McpServerFreshInstallPolicy.EP, emptyList(), disposable)
    val settings = McpServerSettingsImpl()

    settings.noStateLoaded()

    assertThat(settings.enableMcpServer).isFalse()
    assertThat(settings.consent).isEqualTo(McpServerConsent.NOT_ASKED)
    assertThat(settings.enabledByFreshInstallPolicy).isFalse()
  }

  @Test
  fun `a policy that opts out leaves the server off`(@TestDisposable disposable: Disposable) {
    installPolicy(disposable, enabled = false)
    val settings = McpServerSettingsImpl()

    settings.noStateLoaded()

    assertThat(settings.enableMcpServer).isFalse()
    assertThat(settings.enabledByFreshInstallPolicy).isFalse()
  }

  @Test
  fun `an install that already runs the server counts as consented`() {
    val settings = McpServerSettingsImpl()

    settings.loadState(McpServerSettingsImpl.MyState().also { it.enableMcpServer = true })

    assertThat(settings.consent).isEqualTo(McpServerConsent.GRANTED)
  }

  /**
   * A restart before the user answers must not grant the consent. The product turned the server on, so the state on
   * disk looks like a server the user turned on, and only the mark tells them apart.
   */
  @Test
  fun `a restart of an unanswered fresh install still owes an answer`() {
    val settings = McpServerSettingsImpl()

    settings.loadState(McpServerSettingsImpl.MyState().also {
      it.enableMcpServer = true
      it.enabledByFreshInstallPolicy = true
    })

    assertThat(settings.consent).isEqualTo(McpServerConsent.NOT_ASKED)
  }

  @Test
  fun `an install that has the server off is not consented`() {
    val settings = McpServerSettingsImpl()

    settings.loadState(McpServerSettingsImpl.MyState().also { it.enableMcpServer = false })

    assertThat(settings.consent).isEqualTo(McpServerConsent.NOT_ASKED)
  }

  @Test
  fun `an existing install starts the server when the product asks for it`(@TestDisposable disposable: Disposable) {
    installPolicy(disposable, enabled = false, existingEnabled = true)
    val settings = McpServerSettingsImpl()

    settings.loadState(McpServerSettingsImpl.MyState().also { it.enableMcpServer = false })

    assertThat(settings.enableMcpServer).isTrue()
    assertThat(settings.consent).isEqualTo(McpServerConsent.NOT_ASKED)
    assertThat(settings.enabledByFreshInstallPolicy).isTrue()
  }

  @Test
  fun `an existing enabled server keeps its consent migration`(@TestDisposable disposable: Disposable) {
    installPolicy(disposable, enabled = false, existingEnabled = true)
    val settings = McpServerSettingsImpl()

    settings.loadState(McpServerSettingsImpl.MyState().also { it.enableMcpServer = true })

    assertThat(settings.enableMcpServer).isTrue()
    assertThat(settings.consent).isEqualTo(McpServerConsent.GRANTED)
    assertThat(settings.enabledByFreshInstallPolicy).isFalse()
  }

  @Test
  fun `a recorded refusal prevents existing install enablement`(@TestDisposable disposable: Disposable) {
    installPolicy(disposable, enabled = false, existingEnabled = true)
    val settings = McpServerSettingsImpl()

    settings.loadState(McpServerSettingsImpl.MyState().also { it.consent = McpServerConsent.DENIED })

    assertThat(settings.enableMcpServer).isFalse()
    assertThat(settings.consent).isEqualTo(McpServerConsent.DENIED)
    assertThat(settings.enabledByFreshInstallPolicy).isFalse()
  }

  @Test
  fun `a recorded refusal survives loadState`() {
    val settings = McpServerSettingsImpl()

    settings.loadState(McpServerSettingsImpl.MyState().also { it.consent = McpServerConsent.DENIED })

    assertThat(settings.consent).isEqualTo(McpServerConsent.DENIED)
  }

  private fun installPolicy(disposable: Disposable, enabled: Boolean, existingEnabled: Boolean = false) {
    val policy = object : McpServerFreshInstallPolicy {
      override fun isServerEnabledOnFreshInstall(): Boolean = enabled
      override fun isServerEnabledOnExistingInstall(): Boolean = existingEnabled
    }
    ExtensionTestUtil.maskExtensions(McpServerFreshInstallPolicy.EP, listOf<McpServerFreshInstallPolicy>(policy), disposable)
  }
}
