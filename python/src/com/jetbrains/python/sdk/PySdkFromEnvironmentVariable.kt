// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.Path
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.util.EnvironmentUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.getOr
import com.jetbrains.python.venvReader.tryResolvePath
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import java.nio.file.InvalidPathException
import java.nio.file.Paths


// See https://www.jetbrains.com/help/pycharm/configure-an-interpreter-using-command-line.html
/**
 * Configures sdk using [PYCHARM_PYTHON_PATH_ENV] env or [PYCHARM_PYTHON_PATH_PROPERTY] or other envs and properties provided by caller.
 * Create with [create], then call [configureSdkForModules].
 */
@ApiStatus.Internal
class PySdkFromEnvironmentVariable private constructor(
  private val project: Project,
  private val python: PythonBinary,
) {
  init {
    check(project.getEelDescriptor() == python.getEelDescriptor()) { "$python can't be used for $project" }
  }

  companion object {
    private const val PYCHARM_PYTHON_PATH_PROPERTY: String = "PycharmPythonPath"
    const val PYCHARM_PYTHON_PATH_ENV: String = "PYCHARM_PYTHON_PATH"

    private val LOG = logger<PySdkFromEnvironmentVariable>()

    /**
     * Creates tool to configure modules on [project] (you can't use modules from another project).
     * Along with built-in properties and env names, you can provide [propertyName] (for [System.getProperty])
     * and [envVarName] (for [EnvironmentUtil.getEnvironmentMap]).
     *
     * Return `null` if all properties and envs are empty. If they aren't, but contain invalid/broken path, returns error.
     * Otherwise, returns tool ready to use.
     * Call [getOrLog] for logging
     */
    fun create(
      project: Project,
      propertyName: String? = null,
      envVarName: String? = null,
    ): Result<PySdkFromEnvironmentVariable, MessageError>? {
      val descriptor = project.getEelDescriptor()
      val pathStr: @NlsSafe String = propertyName?.let { System.getProperty(it) }
                                     ?: envVarName?.let { EnvironmentUtil.getValue(it) }
                                     ?: System.getProperty(PYCHARM_PYTHON_PATH_PROPERTY)
                                     ?: EnvironmentUtil.getValue(PYCHARM_PYTHON_PATH_ENV)
                                     ?: return null
      val pythonBinary = try {
        Path(pathStr, descriptor)
      }
       catch (e: EelPathException) {
         LOG.info("Path $pathStr isn't path on $descriptor", e)
         try {
           Paths.get(pathStr)
         }
         catch (e: InvalidPathException) {
           LOG.info("Path isn't local path", e)
           null
         }
       } ?: return PyResult.localizedError(PyBundle.message("sdk.configuration.path.wrong", pathStr, descriptor.name))

      if (pythonBinary.getEelDescriptor() != project.getEelDescriptor()) {
        return PyResult.localizedError(
          PyBundle.message("sdk.configuration.path.on.wrong.descriptor",
                           pythonBinary,
                           project.getEelDescriptor().name)
        )
      }
      if (pythonBinary.getEelDescriptor() != LocalEelDescriptor) {
        return PyResult.localizedError(PyBundle.message("sdk.configuration.path.remote.not.supported", pythonBinary))
      }

      return PyResult.success(PySdkFromEnvironmentVariable(project, pythonBinary))
    }
  }

  /**
   * Creates SDK from [python] (or takes the one that exists) and sets it for [modules].
   * All modules **must** be in the same [project].
   * Use [configureSdkForModulesLogIfError] to log errors.
   */
  @CheckReturnValue
  suspend fun configureSdkForModules(modules: Array<Module> = project.modules): PyResult<Unit> {
    python.validatePythonAndGetInfo()
      .getOr(PyBundle.message("sdk.configuration.path.python.invalid", python)) { return it }

    // Skip modules with same SDK
    val modules = modules.filter {
      tryResolvePath(it.findPythonSdk()?.homePath) != python
    }

    if (modules.isEmpty()) {
      return Result.success(Unit)
    }

    val sdk = withSdkConfigurationLock(project) {
      createLocalSdkGuessingTypeByPath(python, ModuleOrProject.ProjectOnly(project))
    }.getOr(PyBundle.message("sdk.configuration.path.cant.create.sdk", python)) { return it }



    withSdkConfigurationLock(project) {
      for (module in modules) {
        check(module.project == project) { "Module $module is not in $project" }
        module.pythonSdk = sdk
      }
    }
    return Result.success(Unit)
  }

  suspend fun configureSdkForModulesLogIfError(log: Logger, modules: Array<Module> = project.modules) {
    when (val r = configureSdkForModules(modules)) {
      is Result.Failure -> {
        log.warn("Failed to configure with python ${python} : ${r.error}")
      }
      is Result.Success -> {
        log.info("Configured modules with ${python}")
      }
    }
  }

  override fun toString(): String =
    "PySdkFromEnvironmentVariable(project=$project, python=$python)"
}


@ApiStatus.Internal
fun Result<PySdkFromEnvironmentVariable, MessageError>?.getOrLog(log: Logger): PySdkFromEnvironmentVariable? =
  when (val r = this) {
    null -> null
    is Result.Failure -> {
      log.warn("SDK configuration requested, but failed: ${r.error}")
      null
    }
    is Result.Success -> r.result
  }