package com.intellij.ide.starter.driver.reused.recovery

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.utility
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.remote.Window
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.project.NoProject
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private fun Driver.waitForIdeFrame() {
  waitFor(
    message = "Reused IDE frame is visible",
    timeout = PROJECT_RECOVERY_TIMEOUT,
    getter = { visibleNonTransientWindowsData() },
    checker = { windows -> windows.any { it.isIdeFrameWindow() } },
    errorMessage = { windows -> "Reused IDE frame is not visible. Visible windows: ${windows.joinToString(", ") { it.toReadableString() }}" },
  )
}

internal fun BackgroundRun.waitForIdeFrameIfNeeded() {
  if (runContext.testContext.ide.vmOptions.hasHeadlessMode() || runContext.testContext.testCase.projectInfo == NoProject) return
  driver.waitForIdeFrame()
}

internal fun Driver.checkResponsiveForRecovery() {
  callWithTimeout(IDE_RESPONSIVENESS_TIMEOUT, "Check IDE responsiveness") {
    withContext(OnDispatcher.EDT, LockSemantics.WRITE_ACTION) {
      check(utility<SwingUtilitiesRemote>().isEventDispatchThread()) { "Recovery responsiveness probe did not run on EDT with write lock" }
    }
  }
}

/**
 * Disposes every visible window that is not part of the reused baseline.
 *
 * The first recovery step. A modal dialog the previous test left open pumps its own events on the EDT, so every
 * later recovery call would queue behind it until the whole recovery timed out.
 */
internal fun Driver.closeWindowsLeftByPreviousTest(initialVisibleWindowData: List<VisibleWindowData>) {
  callWithTimeout(WINDOW_DISPOSAL_TIMEOUT, "Close windows left by the previous test") {
    step("Close windows left by the previous test") {
      withContext(OnDispatcher.EDT) {
        extraWindows(visibleWindowRefs().map { it to it.getVisibleWindowData() }, initialVisibleWindowData) { (_, data) -> data }
          // Extra project frames are left alone: closeProjects and reopenProjects below restore the baseline set.
          .filterNot { (_, data) -> data.isIdeFrameWindow() }
          .forEach { (window, data) -> disposeWindowLeftByPreviousTest(window, data) }
      }
    }
  }
}

private fun disposeWindowLeftByPreviousTest(window: Window, data: VisibleWindowData) {
  logOutput("[ReusedRecovery] Disposing window left by the previous test: ${data.toReadableString()}")
  try {
    window.dispose()
  }
  catch (t: Throwable) {
    // checkNoUnexpectedWindows still fails the recovery if the window survived, which falls back to a restart.
    logError("[ReusedRecovery] Failed to dispose window left by the previous test: ${data.toReadableString()}", t)
  }
}

internal fun Driver.checkNoUnexpectedWindows(initialVisibleWindowData: List<VisibleWindowData>) {
  step("Check no extra windows") {
    val unexpectedWindows = extraWindows(visibleNonTransientWindowsData(), initialVisibleWindowData) { it }
    if (unexpectedWindows.isNotEmpty()) {
      error("There are unexpected windows: ${unexpectedWindows}, expected: ${initialVisibleWindowData.joinToString(", ")}")
    }
  }
}

/**
 * The windows of [windows] that are not in [baseline].
 *
 * Each baseline entry matches one window, so a second copy of a baseline window is extra.
 */
private fun <T> extraWindows(windows: List<T>, baseline: List<VisibleWindowData>, data: (T) -> VisibleWindowData): List<T> {
  val unmatchedBaseline = baseline.toMutableList()
  return windows.filterNot { unmatchedBaseline.remove(data(it)) }
}

internal fun Driver.visibleNonTransientWindowsData(): List<VisibleWindowData> {
  return withContext(OnDispatcher.EDT) {
    visibleWindowRefs().map { it.getVisibleWindowData() }.filterNot { it.isTransientWindow() }
  }
}

private fun Driver.visibleWindowRefs(): List<Window> {
  return utility(Window::class).getWindows()
    .filter { it.isShowing() && it.isDisplayable() }
}

private fun Window.getVisibleWindowData(): VisibleWindowData {
  val className = getClass().getName()
  return VisibleWindowData(
    className = className,
    accessibleName = if (className == IDE_FRAME_CLASS) null else getAccessibleContext()?.getAccessibleName(),
  )
}

/** An anonymous Swing popup can appear briefly during startup, so it is no part of a stable baseline. */
private fun VisibleWindowData.isTransientWindow(): Boolean {
  return className == ANONYMOUS_SWING_POPUP_CLASS && accessibleName == null
}

private fun VisibleWindowData.isIdeFrameWindow(): Boolean {
  return className == IDE_FRAME_CLASS
}

private fun VisibleWindowData.toReadableString(): String {
  return "${className}(accessibleName=${accessibleName})"
}


@Remote("javax.swing.SwingUtilities")
private interface SwingUtilitiesRemote {
  fun isEventDispatchThread(): Boolean
}

private const val ANONYMOUS_SWING_POPUP_CLASS = "javax.swing.JWindow"
private const val IDE_FRAME_CLASS = "com.intellij.openapi.wm.impl.IdeFrameImpl"

private val PROJECT_RECOVERY_TIMEOUT = 1.minutes
private val IDE_RESPONSIVENESS_TIMEOUT = 10.seconds
private val WINDOW_DISPOSAL_TIMEOUT = 1.minutes
