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
import com.jetbrains.python.Result
import com.jetbrains.python.orLogException
import com.jetbrains.python.packaging.PyPackageVersionComparator
import com.jetbrains.python.packaging.cache.PythonPackageCache
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.cache.impl.InMemorySearchPage
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.conda.execution.getCondaBinToExecute
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.flavors.conda.addCondaPythonToTargetCommandLine
import com.jetbrains.python.sdk.targetEnvConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service
class CondaPackageCache : PythonPackageCache {
  override val size: Int
    get() = cache.size
  
  @Volatile
  private var cache: Map<String, List<String>> = emptyMap()
  private val lock = Mutex()
  private var loadInProgress: Boolean = false

  override operator fun contains(name: String): Boolean = name in cache

  override fun search(prefix: String, pageSize: Int): PythonPackageSearchResult {
    val needleLowercase = prefix.lowercase()
    val matches = cache.keys.asSequence().filter { it.lowercase().startsWith(needleLowercase) }.toList()

    return InMemorySearchPage.resultFromMatches(matches, pageSize)
  }

  suspend fun reloadCache(sdk: Sdk, project: Project, force: Boolean = false): Result<Unit, IOException> {
    lock.withLock {
      if ((cache.isNotEmpty() && !force) || loadInProgress) {
        return Result.Success(Unit)
      }

      loadInProgress = true
    }

    try {
      refreshAll(sdk, project)
    }
    finally {
      lock.withLock {
        loadInProgress = false
      }
    }

    return Result.Success(Unit)
  }

  private suspend fun refreshAll(sdk: Sdk, project: Project) {
    withContext(Dispatchers.IO) {
      val targetConfig = sdk.targetEnvConfiguration
      val binaryToExec = sdk.getCondaBinToExecute()
      val envs = PyCondaEnv.getEnvs(binaryToExec).orLogException(thisLogger()) ?: return@withContext
      val baseConda = envs.firstOrNull { it.envIdentity is PyCondaEnvIdentity.UnnamedEnv && it.envIdentity.isBase }
      if (baseConda == null) {
        thisLogger().warn("No base conda environment found, skipping package cache refresh")
        return@withContext
      }

      val helpersAware = PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, project)
      val communityHelpers = helpersAware.preparePyCharmHelpers().helpers.first()


      val targetReq = targetConfig?.createEnvironmentRequest(project) ?: LocalTargetEnvironmentRequest()
      val commandLineBuilder = TargetedCommandLineBuilder(targetReq)
      val targetEnv = targetReq.prepareEnvironment(TargetProgressIndicator.EMPTY)


      val helpersPath = communityHelpers.targetPathFun.apply(targetEnv)

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
        .mapValues { it.value.distinct().sortedWith(PyPackageVersionComparator.STR_COMPARATOR.reversed()) }
        .toMap()

      cache = packages
    }
  }

  operator fun get(name: String): List<String>? = cache[name]
}