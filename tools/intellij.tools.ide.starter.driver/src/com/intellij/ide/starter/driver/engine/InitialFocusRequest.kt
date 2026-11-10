package com.intellij.ide.starter.driver.engine

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.driver.client.Driver
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.components.common.bringStartWindowToFront
import com.intellij.driver.sdk.ui.components.common.requestInitialFocus
import com.intellij.tools.ide.util.common.logError

/**
 * Brings the project frame to the front once per launch, on a Driver call that finds a shown frame.
 *
 * The runner calls [requestIfNeeded] from [Driver.beforeCall], and each launch has a new instance.
 * Until the request is done, it tries on each Driver call.
 * Meanwhile it brings a dialog or the Welcome screen to the front once per launch.
 * A failed request goes to the log and ends the request.
 */
internal class InitialFocusRequest {
  @Volatile
  private var done = false
  private var windowClicked = false

  fun requestIfNeeded(driver: Driver) {
    if (done) return
    synchronized(this) {
      if (done) return
      try {
        if (!driver.isConnected) return
        driver.withContext(OnDispatcher.DEFAULT) {
          done = requestInitialFocus()
          if (!done && !windowClicked) windowClicked = bringStartWindowToFront()
        }
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        logError("The request to bring the IDE frame to the front failed", e)
        done = true
      }
    }
  }
}
