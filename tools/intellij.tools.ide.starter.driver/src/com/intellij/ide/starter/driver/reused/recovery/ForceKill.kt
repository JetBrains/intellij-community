package com.intellij.ide.starter.driver.reused.recovery

import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.driver.engine.BackgroundRun
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Force-kills [bgRun] and waits until every process of it is dead.
 *
 * The next IDE of a reused session starts on the same shared system directory, so it must not start before this
 * returns. The shared system directory stays as the kill left it, and the next IDE opens it as after a crash.
 */
fun forceKillAndAwaitDeath(bgRun: BackgroundRun) {
  bgRun.forceKill()
  awaitEveryProcessDead(bgRun)
}

/** Waits until every process of [bgRun] is dead, and throws when one is still alive after the timeout. */
internal fun awaitEveryProcessDead(bgRun: BackgroundRun) {
  waitFor("Every force-killed IDE process is dead", PROCESS_DEATH_TIMEOUT, interval = 100.milliseconds) {
    bgRun.ownedProcesses.none { it.isAlive }
  }
}

// How long a force-killed IDE gets to disappear before the next IDE of the session may start.
private val PROCESS_DEATH_TIMEOUT = 1.minutes
