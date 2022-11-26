// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.withBackgroundProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.cache.PythonPackageCache
import com.jetbrains.python.packaging.common.RANKING_AWARE_PACKAGE_NAME_COMPARATOR
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.flavors.conda.*
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.targetEnvConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Service
class CondaPackageCache : PythonPackageCache<String> {
  private var cache: Map<String, List<String>> = emptyMap()

  override val packages: List<String>
    get() = cache.keys.toList()

  suspend fun refreshAll(sdk: Sdk, project: Project) {
    withContext(Dispatchers.IO) {
      val pathOnTarget = (sdk.getOrCreateAdditionalData().flavorAndData.data as PyCondaFlavorData).env.fullCondaPathOnTarget
      val targetConfig = sdk.targetEnvConfiguration

      val command = PyCondaCommand(pathOnTarget, targetConfig, project)
      val baseConda = PyCondaEnv.getEnvs(command).getOrThrow()
        .first { it.envIdentity is PyCondaEnvIdentity.UnnamedEnv && it.envIdentity.isBase }

      val helpersAware = PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, project)
      val helpers = helpersAware.preparePyCharmHelpers()


      val targetReq = targetConfig?.createEnvironmentRequest(project) ?: LocalTargetEnvironmentRequest()
      val commandLineBuilder = TargetedCommandLineBuilder(targetReq)
      val targetEnv = targetReq.prepareEnvironment(TargetProgressIndicator.EMPTY)


      val helpersPath = helpers.apply(targetEnv)

      // SDK associated with another conda env, not the base one, so we do not pass it not to activate wrong conda
      addCondaPythonToTargetCommandLine(commandLineBuilder, baseConda, null)
      commandLineBuilder.addParameter("$helpersPath/conda_packaging_tool.py")
      commandLineBuilder.addParameter("listall")

      val targetedCommandLine = commandLineBuilder.build()

      val process = targetEnv.createProcess(targetedCommandLine)


      val commandLine = targetedCommandLine.collectCommandsSynchronously()
      val commandLineString = StringUtil.join(commandLine, " ")
      val handler = CapturingProcessHandler(process, targetedCommandLine.charset, commandLineString)
      thisLogger().debug("Running conda packaging tool to read available conda packages")
      val result = withBackgroundProgressIndicator(project, PyBundle.message("conda.packaging.cache.update.progress"), cancellable = true) {
        handler.runProcess(10 * 60 * 1000)
      }
      result.checkSuccess(thisLogger())

      val packages = result.stdout.lineSequence()
        .map { it.split("\t") }
        .filterNot { it.size < 2 }
        .filterNot { it[0].startsWith("r-") } // todo[akniazev]: make sure it's the best way to get rid of R packages
        .groupBy({ it[0] }, { it[1] })
        .toSortedMap(RANKING_AWARE_PACKAGE_NAME_COMPARATOR)

      withContext(Dispatchers.Main) {
        cache = packages
      }
    }
  }

  override fun isEmpty(): Boolean = cache.isEmpty()
  operator fun get(name: String): List<String>? = cache[name]
  override fun contains(key: String): Boolean = key in cache
}