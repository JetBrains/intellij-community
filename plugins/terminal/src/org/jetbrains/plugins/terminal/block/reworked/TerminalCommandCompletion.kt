// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.idea.AppModeAssertions
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.session.ShellName

@ApiStatus.Internal
object TerminalCommandCompletion {
  /**
   * Can be either the item chosen by the user explicitly, or the item that was fully typed,
   * or just the selected item showing in the lookup at the moment of closing.
   */
  val LAST_SELECTED_ITEM_KEY: Key<LookupElement> = Key.create("TerminalCommandCompletion.LastSelectedItem")

  private const val REGISTRY_KEY = "terminal.new.ui.completion.popup"

  fun isEnabled(project: Project): Boolean {
    return Registry.`is`(REGISTRY_KEY, false)
           && AppModeAssertions.isMonolith()                    // Disable in RemDev at the moment because it is not supported yet
           && project.getEelDescriptor() == LocalEelDescriptor  // Disable in non-local projects for now as it requires additional support
  }

  fun isSupportedForShell(name: ShellName): Boolean {
    return name == ShellName.ZSH || name == ShellName.BASH || ShellName.isPowerShell(name)
  }

  @TestOnly
  fun enableForTests(parentDisposable: Disposable) {
    Registry.get(REGISTRY_KEY).setValue(true, parentDisposable)
  }
}