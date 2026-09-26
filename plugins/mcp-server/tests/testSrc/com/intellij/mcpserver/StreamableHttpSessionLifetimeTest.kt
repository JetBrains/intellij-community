// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.network.SSE_HEARTBEAT_PERIOD_REGISTRY_KEY
import com.intellij.mcpserver.impl.util.network.STREAMABLE_SESSION_IDLE_TIMEOUT_REGISTRY_KEY
import com.intellij.mcpserver.impl.util.network.UNINITIALIZED_SESSION_CLOSED
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import io.ktor.client.HttpClient
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Every wait below is bounded on its own, so this only stops a hang from running to the CI limit. */
private val TEST_TIMEOUT = 120.seconds

private const val NOTIFICATION_STREAM_RECONNECTS = 2

/** A short heartbeat is what makes the server find a dropped notification stream in a fraction of a second. */
private const val FAST_HEARTBEAT_PERIOD_MS = "500"
private val FAST_HEARTBEAT_PERIOD = FAST_HEARTBEAT_PERIOD_MS.toInt().milliseconds

/**
 * The heartbeat loop writes and then delays, so the write that finds a dropped stream comes a period later. Two
 * periods leave room for that write, and for the server to act on it.
 */
private val DROPPED_STREAM_DISCOVERY_PERIOD = FAST_HEARTBEAT_PERIOD * 2

/** Short enough to keep a test that waits for a close quick, long enough not to expire mid-request. */
private const val SHORT_IDLE_TIMEOUT_MS = "1500"

/** A session in use is polled several times per idle timeout, so that a stalled machine cannot fake an idle one. */
private const val CONTINUOUS_USE_IDLE_TIMEOUT_MS = "5000"
private const val CONTINUOUS_USE_POLLS = 10
private val CONTINUOUS_USE_POLL_PERIOD = 1.seconds

/** A client that declares roots is asked for them, and the one in these tests never answers. */
private val ROOTS_CAPABILITY = ClientCapabilities(roots = ClientCapabilities.Roots(listChanged = true))

/** Nothing marks the end of an error that is never reported, and a session's teardown is long over by then. */
private val ERROR_REPORTING_SETTLE_PERIOD = 2.seconds

/**
 * A Streamable HTTP session is identified by its `mcp-session-id`, and it lives independently of any single HTTP
 * request. The standalone GET notification stream may be dropped and reopened at will, and a client that never opens
 * one at all must keep working. A session must not outlive its client either. An abandoned session is closed once it
 * falls idle, and closing a session releases everything the IDE was holding for it. See IJPL-246574.
 */
@TestApplication
class StreamableHttpSessionLifetimeTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val project by projectFixture
  }

  @Test
  @RegistryKey(SSE_HEARTBEAT_PERIOD_REGISTRY_KEY, FAST_HEARTBEAT_PERIOD_MS)
  fun session_outlives_notification_stream_reconnects(): Unit = mcpServerTest {
    initialize()

    for (reconnect in 1..NOTIFICATION_STREAM_RECONNECTS) {
      openAndKillNotificationStream()
      delay(DROPPED_STREAM_DISCOVERY_PERIOD)

      assertThat(trackedSessionIds()).describedAs("sessions the IDE holds after drop #$reconnect").contains(sessionId)
      listTools().assertOk("a request after notification stream drop #$reconnect")
    }
  }

  @Test
  @RegistryKey(STREAMABLE_SESSION_IDLE_TIMEOUT_REGISTRY_KEY, CONTINUOUS_USE_IDLE_TIMEOUT_MS)
  fun session_in_continuous_use_is_never_closed(): Unit = mcpServerTest {
    initialize()

    for (poll in 1..CONTINUOUS_USE_POLLS) {
      delay(CONTINUOUS_USE_POLL_PERIOD)

      listTools().assertOk("request #$poll of a session that never opened a notification stream")
    }
  }

  @Test
  fun delete_closes_the_session_and_a_second_delete_reports_it_gone(): Unit = mcpServerTest {
    initialize()
    val deletedSessionId = sessionId

    deleteSession().assertOk("a delete of a live session")

    listTools().assertSessionNotFound("a request right after the session was deleted")
    deleteSession().assertSessionNotFound("a second delete of the same session")
    awaitSessionClosed(deletedSessionId, "the deleted session")
  }

  /** The legacy transport has no session to delete. Its stream is the session, and the session ends with it. */
  @Test
  fun legacy_sse_session_is_closed_when_its_stream_ends(): Unit = mcpServerTest {
    val log = WatchedLog()
    LoggedErrorProcessor.executeWith(log).use {
      val alreadyTracked = trackedSessionIds()
      withLegacySseSession { legacy ->
        legacy.initialize()
        val legacySessionId = awaitSessionOpened(alreadyTracked)

        legacy.kill()

        awaitSessionClosed(legacySessionId, "the legacy SSE session whose stream was killed")
        legacy.listTools().assertNotFound("a request carrying the id of a killed legacy SSE session")
      }
      delay(ERROR_REPORTING_SETTLE_PERIOD)
    }

    log.assertNoErrors("while a legacy SSE stream was killed")
  }

  @Test
  fun live_sessions_are_closed_when_the_server_stops(): Unit = mcpServerTest {
    initialize()
    val servedSessionId = sessionId

    McpServerService.getInstance().stop()

    awaitSessionClosed(servedSessionId, "the session the server was serving when it stopped")
  }

  /** A short idle timeout is what keeps each of these tests to a few seconds. */
  @Nested
  @RegistryKey(STREAMABLE_SESSION_IDLE_TIMEOUT_REGISTRY_KEY, SHORT_IDLE_TIMEOUT_MS)
  inner class AbandonedSessions {
    @Test
    fun idle_session_is_closed_and_the_client_reinitializes(): Unit = mcpServerTest {
      initialize()
      val abandonedSessionId = sessionId

      awaitSessionClosed(abandonedSessionId, "the abandoned session")

      listTools().assertSessionNotFound("a request carrying the id of a closed session")

      initialize()

      assertThat(sessionId).describedAs("the session opened after the close").isNotEqualTo(abandonedSessionId)
      listTools().assertOk("a request in the session opened after the close")
    }

    /** The server has to create a session before it can tell that the request does not initialize one. */
    @Test
    fun request_that_initializes_no_session_leaks_none(): Unit = mcpServerTest {
      val log = WatchedLog()
      LoggedErrorProcessor.executeWith(log).use {
        listTools().assertBadRequest("a request with neither a session id nor an initialization")

        log.awaitWarning("it closed the session of a request that never initialized one") {
          it.startsWith(UNINITIALIZED_SESSION_CLOSED)
        }
      }
    }

    /**
     * A roots request has nowhere to go when the client never opened a notification stream, so it is still pending
     * when the session is closed. Failing it is part of closing the session, and must not be reported as an IDE error.
     */
    @Test
    fun close_of_a_roots_capable_session_reports_no_errors(): Unit = mcpServerTest {
      val log = WatchedLog()
      LoggedErrorProcessor.executeWith(log).use {
        initialize(ROOTS_CAPABILITY)
        val abandonedSessionId = sessionId
        awaitSessionClosed(abandonedSessionId, "the abandoned session")
        delay(ERROR_REPORTING_SETTLE_PERIOD)
      }

      log.assertNoErrors("while a roots-capable session was closed")
    }
  }

  private fun mcpServerTest(action: suspend McpTestClient.() -> Unit) =
    timeoutRunBlocking(timeout = TEST_TIMEOUT, context = Dispatchers.Default) {
      McpServerService.getInstance().start()
      try {
        HttpClient().use { client ->
          McpTestClient(client, project.basePath).action()
        }
      }
      finally {
        McpServerService.getInstance().stop()
      }
    }
}
