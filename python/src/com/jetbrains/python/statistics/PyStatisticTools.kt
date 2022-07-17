// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.extensions.getSdk
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.pipenv.isPipEnv
import com.jetbrains.python.sdk.poetry.isPoetry

val Project.modules get() = ModuleManager.getInstance(this).modules
val Project.sdks get() = modules.mapNotNull(Module::getSdk)

/**
 * Adds python language and interpreter version if module has sdk
 */
fun getPythonSpecificInfo(module: Module) =
  module.getSdk()?.let { sdk -> getPythonSpecificInfo(sdk) } ?: emptyList()

/**
 * Adds python language and interpreter version
 */
fun getPythonSpecificInfo(sdk: Sdk): List<EventPair<*>> {
  val data = ArrayList<EventPair<*>>()
  data.add(EventFields.Language.with(PythonLanguage.INSTANCE))
  data.add(PYTHON_VERSION.with(sdk.version))
  data.add(PYTHON_IMPLEMENTATION.with(sdk.pythonImplementation))
  data.add(EXECUTION_TYPE.with(sdk.executionType))
  data.add(INTERPRETER_TYPE.with(sdk.interpreterType))
  return data
}

fun registerPythonSpecificEvent(group: EventLogGroup, eventId: String, vararg extraFields: EventField<*>): VarargEventId {
  return group.registerVarargEvent(eventId,
                                   EventFields.Language,
                                   PYTHON_VERSION,
                                   PYTHON_IMPLEMENTATION,
                                   EXECUTION_TYPE,
                                   INTERPRETER_TYPE,
                                   *extraFields)
}

val PYTHON_VERSION = EventFields.StringValidatedByRegexp("python_version", "version")
val PYTHON_IMPLEMENTATION = EventFields.String("python_implementation", listOf("PyPy", "Jython", "Python"))
val EXECUTION_TYPE = EventFields.String("executionType", listOf("local", "Remote_Docker", "Remote_Docker_Compose", "Remote_WSL", "Remote_null", "third_party", "Remote_SSH_Credentials", "Remote_Vagrant", "Remote_Web_Deployment", "Remote_Unknown"))
val INTERPRETER_TYPE = EventFields.String("interpreterType", listOf("pipenv", "condavenv", "virtualenv", "regular", "poetry"))


private val Sdk.version get() = PythonSdkType.getLanguageLevelForSdk(this).toPythonVersion()
private val Sdk.pythonImplementation: String get() = PythonSdkFlavor.getFlavor(this)?.name ?: "Python"
private val Sdk.executionType get(): String = (sdkAdditionalData as? PyRemoteSdkAdditionalDataBase)?.executionType ?: "local"
private val Sdk.interpreterType
  get() = when {
    // The order of checks is important here since e.g. a pipenv is a virtualenv
    isPipEnv -> "pipenv"
    isPoetry -> "poetry"
    PythonSdkUtil.isConda(this) -> "condavenv"
    PythonSdkUtil.isVirtualEnv(this) -> "virtualenv"
    else -> "regular"
  }

private val PyRemoteSdkAdditionalDataBase.executionType: String
  get() = remoteConnectionType.let { type ->
    when {
      type == null -> "Remote_null"
      getPluginInfo(type.javaClass).isDevelopedByJetBrains() -> "Remote_${type.name?.replace(' ', '_')}"
      else -> "third_party"
    }
  }
