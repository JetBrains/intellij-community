package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpServerConsentUi
import com.intellij.mcpserver.settings.McpServerConsent
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.openapi.project.Project
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
internal class McpServerConsentGateTest {
  private val settings = McpServerSettings.getInstance()
  private val originalConsent = settings.consent
  private val originalEnabled = settings.enableMcpServer
  private val originalEnabledByPolicy = settings.enabledByFreshInstallPolicy

  @AfterEach
  fun restoreSettings() {
    settings.consent = originalConsent
    settings.enableMcpServer = originalEnabled
    settings.enabledByFreshInstallPolicy = originalEnabledByPolicy
  }

  @Test
  fun `a granted consent needs no dialog`(): Unit = timeoutRunBlocking {
    val ui = CountingConsentUi(answer = false)
    prepare(McpServerConsent.GRANTED)

    assertThat(newGate(ui).awaitConsent()).isTrue()
    assertThat(ui.callCount.get()).isZero()
  }

  @Test
  fun `a refused consent needs no dialog`(): Unit = timeoutRunBlocking {
    val ui = CountingConsentUi(answer = true)
    prepare(McpServerConsent.DENIED)

    assertThat(newGate(ui).awaitConsent()).isFalse()
    assertThat(ui.callCount.get()).isZero()
  }

  @Test
  fun `an agreement is recorded and the server stays on`(): Unit = timeoutRunBlocking {
    val ui = CountingConsentUi(answer = true)
    prepare(McpServerConsent.NOT_ASKED)

    assertThat(newGate(ui).awaitConsent()).isTrue()
    assertThat(settings.consent).isEqualTo(McpServerConsent.GRANTED)
    assertThat(settings.enableMcpServer).isTrue()
    // The answer replaces the mark, so a later load does not treat this as an unanswered server.
    assertThat(settings.enabledByFreshInstallPolicy).isFalse()
  }

  @Test
  fun `a refusal is recorded and turns the server off`(): Unit = timeoutRunBlocking {
    val ui = CountingConsentUi(answer = false)
    prepare(McpServerConsent.NOT_ASKED)

    assertThat(newGate(ui).awaitConsent()).isFalse()
    assertThat(settings.consent).isEqualTo(McpServerConsent.DENIED)
    assertThat(settings.enableMcpServer).isFalse()
    assertThat(settings.enabledByFreshInstallPolicy).isFalse()
  }

  @Test
  fun `a call is refused when no consent ui exists`(): Unit = timeoutRunBlocking {
    prepare(McpServerConsent.NOT_ASKED)

    assertThat(newGate(null).awaitConsent()).isFalse()
    // The user decided nothing, so a later call must ask again.
    assertThat(settings.consent).isEqualTo(McpServerConsent.NOT_ASKED)
  }

  @Test
  fun `concurrent first calls share one dialog`(): Unit = timeoutRunBlocking {
    val entered = CompletableDeferred<Unit>()
    val released = CompletableDeferred<Unit>()
    val ui = CountingConsentUi(answer = true, entered = entered, released = released)
    prepare(McpServerConsent.NOT_ASKED)
    val gate = newGate(ui)

    val answers = coroutineScope {
      val calls = List(4) { async { gate.awaitConsent() } }
      // Hold the dialog open until every call is in flight, so they really do contend for it.
      entered.await()
      released.complete(Unit)
      calls.awaitAll()
    }

    assertThat(answers).containsOnly(true)
    assertThat(ui.callCount.get()).isEqualTo(1)
  }

  private fun prepare(consent: McpServerConsent) {
    settings.consent = consent
    settings.enableMcpServer = true
    settings.enabledByFreshInstallPolicy = consent == McpServerConsent.NOT_ASKED
  }

  /** The gate skips the dialog on a host that cannot show one, and a test host is exactly that. */
  private fun newGate(consentUi: McpServerConsentUi?) = McpServerConsentGate(consentUi).apply { interactiveHostOverride = true }

  private class CountingConsentUi(
    private val answer: Boolean,
    private val entered: CompletableDeferred<Unit>? = null,
    private val released: CompletableDeferred<Unit>? = null,
  ) : McpServerConsentUi {
    val callCount: AtomicInteger = AtomicInteger()

    override suspend fun askConsent(project: Project?): Boolean {
      callCount.incrementAndGet()
      entered?.complete(Unit)
      released?.await()
      return answer
    }
  }
}
