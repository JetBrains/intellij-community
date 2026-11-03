// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.python.community.common.tools.ToolId
import com.jetbrains.python.errorProcessing.ErrorSink
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/** Describes an environment manager in the Add Interpreter user interface. */
@ApiStatus.Internal
interface PythonEnvironmentManager {
  val toolId: ToolId
  val displayName: @Nls String
  val icon: Icon
  val order: Int
  val sshAutoUploadRequired: Boolean

  fun supports(fileSystem: FileSystem<*>): Boolean
}

/** Gives an Add Interpreter provider the current dialog state. */
@ApiStatus.Internal
class PythonAddInterpreterProviderContext<P : PathHolder>(
  val model: PythonMutableTargetAddInterpreterModel<P>,
  val module: Module?,
  val errorSink: ErrorSink,
  val creationContext: InterpreterCreationContext,
)

/** Adds an environment manager to the Add Interpreter dialog and interpreter menu. */
@ApiStatus.Internal
interface PythonAddInterpreterProvider {
  val manager: PythonEnvironmentManager

  val quickActionName: @Nls String?
    get() = null

  fun supports(creationContext: InterpreterCreationContext): Boolean = true

  fun <P : PathHolder> createNewEnvironment(
    context: PythonAddInterpreterProviderContext<P>,
  ): PythonNewEnvironmentCreator<P>? = null

  fun <P : PathHolder> selectExistingEnvironment(
    context: PythonAddInterpreterProviderContext<P>,
  ): PythonExistingEnvironmentConfigurator<P>? = null

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PythonAddInterpreterProvider> =
      ExtensionPointName.create("Pythonid.addInterpreterProvider")
  }
}

internal class PythonAddInterpreterProviders(
  private val providers: List<PythonAddInterpreterProvider> = PythonAddInterpreterProvider.EP_NAME.extensionList,
) {
  init {
    val duplicate = providers.groupingBy { it.manager.toolId }.eachCount().entries.firstOrNull { it.value > 1 }?.key
    require(duplicate == null) { "Duplicate Add Interpreter provider ID: ${duplicate?.id}" }
  }

  fun providersFor(
    fileSystem: FileSystem<*>,
    creationContext: InterpreterCreationContext,
  ): List<PythonAddInterpreterProvider> =
    providers.filter { it.manager.supports(fileSystem) && it.supports(creationContext) }.sortedBy { it.manager.order }

  fun quickActionProviders(): List<PythonAddInterpreterProvider> =
    providers.filter { it.quickActionName != null }.sortedBy { it.manager.order }
}
