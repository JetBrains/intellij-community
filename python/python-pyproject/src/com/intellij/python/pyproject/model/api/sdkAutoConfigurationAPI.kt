package com.intellij.python.pyproject.model.api

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.model.internal.SdkSetupCallBack
import com.intellij.python.pyproject.model.internal.autoConfigureSdk
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithSdkCreator
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithToolBase

/**
 *
 * An instruction to configure SDK for [module] using [toolId].
 * [autoConfigureSdk] follows this instruction, but do not call it, use:
 * * [autoConfigureSdkCompletely]
 * * [autoConfigureSdkExistingOnly]
 * * [autoConfigureSdkDoNotCreateFiles]
 */
sealed class SdkForModuleConfigInstruction(internal val module: Module) {
  abstract val toolId: ToolId

  /**
   * Should be created by means of [createSdkInfoWithTool]
   */
  class CreateSdkInfoWrapper internal constructor(module: Module, val createSdkInfoWithTool: CreateSdkInfoWithTool) :
    SdkForModuleConfigInstruction(module) {
    override val toolId: ToolId = createSdkInfoWithTool.toolId
  }

  /**
   * Should be same as [parentModule]
   */
  class SameAs internal constructor(module: Module, val parentModule: Module, override val toolId: ToolId) :
    SdkForModuleConfigInstruction(module) {
    init {
      check(parentModule != module) { "$parentModule can't be parent of the same module $module" }
    }
  }
}


/**
 * Configure sdk only if files (e.g. `.venv`) exist on disk.
 */
suspend fun SdkForModuleConfigInstruction.autoConfigureSdkDoNotCreateFiles(): SdkConfigurationResult<CreateSdkNotFilesResult> =
  autoConfigureSdk { infoWithCreator ->
    when (val r = infoWithCreator.createSdkInfo) {
      is CreateSdkInfo.ExistingEnv -> SdkSetupCallBack.Accepted { CreateSdkNotFilesResult.SdkCreationError(it) }
      is CreateSdkInfo.WillCreateEnv -> {
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
 * Every setup request is denied and reported back as [SdkConfigurationResult.NotConfigured] carrying the
 * [CreateSdkInfoWithToolBase] that describes what *would* have been done.
 */
suspend fun SdkForModuleConfigInstruction.autoConfigureSdkExistingOnly(): SdkConfigurationResult<CreateSdkInfoWithToolBase<CreateSdkInfoWithSdkCreator>> =
  autoConfigureSdk {
    SdkSetupCallBack.Denied(it)
  }

/**
 * Configure SDK and even create files if needed (the ultimate approach that does its best to configure SDK)
 */
suspend fun SdkForModuleConfigInstruction.autoConfigureSdkCompletely(): SdkConfigurationResult<PyError> = autoConfigureSdk {
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
   * No files exist on disk (check [createInfo] to see how to create them: [CreateSdkInfo.WillCreateEnv.sdkCreator])
   */
  class NoFiles internal constructor(val createInfo: CreateSdkInfoWithToolBase<CreateSdkInfo.WillCreateEnv>) : CreateSdkNotFilesResult
}

/**
 * Subset of [SdkConfigurationResult] without [SdkConfigurationResult.Configured]
 */
sealed interface SdkConfigurationError<T : Any>

/**
 * Outcome of [SdkForModuleConfigInstruction.autoConfigureSdk]
 */
sealed interface SdkConfigurationResult<T : Any> {
  /**
   * [sdk] configured
   */
  class Configured<T : Any> internal constructor(val sdk: Sdk) : SdkConfigurationResult<T>

  /**
   * SDK configuration failed due to [reason]
   */
  class NotConfigured<T : Any> internal constructor(val reason: T) :
    SdkConfigurationResult<T>, SdkConfigurationError<T>

  /**
   * To configure SDK [tool] needs to be installed
   */
  class ToolNotInstalled<T : Any> internal constructor(val tool: CreateSdkInfo.WillInstallTool) :
    SdkConfigurationResult<T>, SdkConfigurationError<T>

  /**
   * Module should have the same sdk as [parentModule], but [parentModule] has no SDK due to [reason].
   * `null` is the same as `null` in [ModuleSdkState.NoSdk.sdkConfigInstruction]: [parentModule] has no suggestions.
   */
  class ParentHasNoSdk<T : Any> internal constructor(
    val parentModule: Module,
    val reason: SdkConfigurationError<T>?,
  ) : SdkConfigurationResult<T>, SdkConfigurationError<T>
}
