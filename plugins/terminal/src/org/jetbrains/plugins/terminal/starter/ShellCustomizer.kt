// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.starter

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Experimental
interface ShellCustomizer {
  /**
   * Customizes the environment variables of a new shell session.
   * The method is called on a background thread without a read action.
   *
   * @param shellExecOptions the parameters used to execute the shell session
   */
  @RequiresBackgroundThread(generateAssertion = false)
  @RequiresReadLockAbsence(generateAssertion = false)
  fun customizeExecOptions(project: Project, shellExecOptions: ShellExecOptions) {}

  /**
   * Retrieves the starting directory for the given project.
   * 
   * The method can be called on any thread without a read action.
   *
   * @return the starting directory, or `null` if no directory is specified
   *         In case of `null`, the default start directory will be used.
   */
  fun getStartDirectory(project: Project): Path? = null

  /**
   * @return a configurable for customizer-specific options
   * The method is called on EDT.
   */
  @RequiresEdt(generateAssertion = false)
  fun getConfigurable(project: Project): UnnamedConfigurable? = null

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<ShellCustomizer> =
      ExtensionPointName("org.jetbrains.plugins.terminal.shellCustomizer")
  }

}
