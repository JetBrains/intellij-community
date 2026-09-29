package com.intellij.python.pyproject.model.api

import com.intellij.openapi.module.Module
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.project.PyProject
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.model.internal.SdkSetupCallBack
import com.intellij.python.pyproject.model.internal.autoConfigureSdk
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfoWithInterpreterCreator
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithToolBase

/**
 *
 * An instruction to configure SDK for [pyProject] using [toolId].
 * [autoConfigureSdk] follows this instruction, but do not call it, use:
 * * [autoConfigureSdkCompletely]
 * * [autoConfigureSdkExistingOnly]
 * * [autoConfigureSdkDoNotCreateFiles]
 */
sealed class SdkForModuleConfigInstruction(val pyProject: PyProject) {
  abstract val toolId: ToolId

  /** The module the SDK is written to. The project model stores an SDK per module. */
  internal val module: Module get() = pyProject.residesOnModule

  /**
   * Should be created by means of [createSdkInfoWithTool]
   */
  class CreateSdkInfoWrapper internal constructor(pyProject: PyProject, val createSdkInfoWithTool: CreateSdkInfoWithTool) :
    SdkForModuleConfigInstruction(pyProject) {
    override val toolId: ToolId = createSdkInfoWithTool.toolId
  }

  /**
   * Should use the same interpreter as [parent]
   */
  class SameAs internal constructor(pyProject: PyProject, val parent: PyProject, override val toolId: ToolId) :
    SdkForModuleConfigInstruction(pyProject) {
    init {
      check(parent != pyProject) { "$parent can't be parent of itself" }
    }
  }
}


/**
 * Configure sdk only if files (e.g. `.venv`) exist on disk.
 */
suspend fun SdkForModuleConfigInstruction.autoConfigureSdkDoNotCreateFiles(): InterpreterConfigurationResult<CreateSdkNotFilesResult> =
  autoConfigureSdk { infoWithCreator ->
    when (val r = infoWithCreator.createSdkInfo) {
      is CreateInterpreterInfo.ExistingEnv -> SdkSetupCallBack.Accepted { CreateSdkNotFilesResult.SdkCreationError(it) }
      is CreateInterpreterInfo.WillCreateEnv -> {
        val willCreateEnv = CreateSdkInfoWithToolBase(r, infoWithCreator.toolId)
        SdkSetupCallBack.Denied(CreateSdkNotFilesResult.NoFiles(willCreateEnv))
      }
    }
  }

/**
 * Configure the module SDK only if it is *already registered* (or can be inherited from a parent module that already
 * has one via [SdkForModuleConfigInstruction.SameAs]).
 *
 * Nothing is created here: no environment files are written and no SDK is registered from an existing on-disk env.
 * Every setup request is denied and reported back as [InterpreterConfigurationResult.NotConfigured] carrying the
 * [CreateSdkInfoWithToolBase] that describes what *would* have been done.
 */
suspend fun SdkForModuleConfigInstruction.autoConfigureSdkExistingOnly(): InterpreterConfigurationResult<CreateSdkInfoWithToolBase<CreateInterpreterInfoWithInterpreterCreator>> =
  autoConfigureSdk {
    SdkSetupCallBack.Denied(it)
  }

/**
 * Configure SDK and even create files if needed (the ultimate approach that does its best to configure SDK)
 */
suspend fun SdkForModuleConfigInstruction.autoConfigureSdkCompletely(): InterpreterConfigurationResult<PyError> = autoConfigureSdk {
  SdkSetupCallBack.Accepted { it }
}

/**
 * Result for [autoConfigureSdkDoNotCreateFiles]
 */
sealed interface CreateSdkNotFilesResult {
  /**
   * We've tried to create an SDK, but failed to due to [error] (e.g. python installation exists, but broken)
   */
  class SdkCreationError internal constructor(val error: PyError) : CreateSdkNotFilesResult

  /**
   * No files exist on disk (check [createInfo] to see how to create them: [CreateInterpreterInfo.WillCreateEnv.interpreterCreator])
   */
  class NoFiles internal constructor(val createInfo: CreateSdkInfoWithToolBase<CreateInterpreterInfo.WillCreateEnv>) : CreateSdkNotFilesResult
}

/**
 * Subset of [InterpreterConfigurationResult] without [InterpreterConfigurationResult.Configured]
 */
sealed interface InterpreterConfigurationError<T : Any>

/**
 * Outcome of [SdkForModuleConfigInstruction.autoConfigureSdk]
 */
sealed interface InterpreterConfigurationResult<T : Any> {
  /**
   * [interpreter] configured. The snapshot of [com.intellij.python.pyproject.model.evolution.EvoPyProjectModel]
   * already holds it.
   */
  class Configured<T : Any> internal constructor(val interpreter: PythonInterpreter) : InterpreterConfigurationResult<T>

  /**
   * SDK configuration failed due to [reason]
   */
  class NotConfigured<T : Any> internal constructor(val reason: T) :
    InterpreterConfigurationResult<T>, InterpreterConfigurationError<T>

  /**
   * To configure SDK [tool] needs to be installed
   */
  class ToolNotInstalled<T : Any> internal constructor(val tool: CreateInterpreterInfo.WillInstallTool) :
    InterpreterConfigurationResult<T>, InterpreterConfigurationError<T>

  /**
   * The project should have the same interpreter as [parent], but [parent] has none due to [reason].
   * `null` is the same as `null` in [ModuleSdkState.NoSdk.sdkConfigInstruction]: [parent] has no suggestions.
   */
  class ParentHasNoInterpreter<T : Any> internal constructor(
    val parent: PyProject,
    val reason: InterpreterConfigurationError<T>?,
  ) : InterpreterConfigurationResult<T>, InterpreterConfigurationError<T>
}
