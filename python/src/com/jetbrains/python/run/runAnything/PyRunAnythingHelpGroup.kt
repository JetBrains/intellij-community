// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run.runAnything

import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.ide.actions.runAnything.groups.RunAnythingHelpGroup

/**
 * @author vlan
 */
class PyRunAnythingHelpGroup : RunAnythingHelpGroup<RunAnythingProvider<*>>() {
  override fun getProviders() =
    RunAnythingProvider.EP_NAME.extensions.filter { it is PyConsoleRunAnythingProvider ||
                                                    it is PyRunAnythingProvider }

  override fun getTitle() = "Python"
}