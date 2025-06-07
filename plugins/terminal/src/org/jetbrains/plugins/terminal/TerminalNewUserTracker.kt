// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import org.jetbrains.plugins.terminal.TerminalNewUserTracker.Companion.clearNewUserValue

internal class TerminalNewUserTracker : AppLifecycleListener {
  init {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode ||
        application.isHeadlessEnvironment ||
        // We need to track the new user state only in monolith or JetBrains Client.
        AppMode.isRemoteDevHost()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun appStarted() {
    if (ConfigImportHelper.isNewUser()) {
      // Remember to use it later, because the user may close the IDE several times before our use case is executed.
      PropertiesComponent.getInstance().setValue(IS_NEW_USER_PROPERTY, true)
    }
  }

  companion object {
    private const val IS_NEW_USER_PROPERTY = "terminal.is.new.user"

    /**
     * True if a user launched the IDE after installation,
     * without importing any settings and [clearNewUserValue] was not called yet.
     *
     * Can be true even on second and further launches of the IDE until [clearNewUserValue] is called.
     */
    fun isNewUser(): Boolean {
      return PropertiesComponent.getInstance().getBoolean(IS_NEW_USER_PROPERTY, false)
    }

    /** Clear the stored value if it is no more necessary. */
    fun clearNewUserValue() {
      PropertiesComponent.getInstance().setValue(IS_NEW_USER_PROPERTY, false)
    }
  }
}