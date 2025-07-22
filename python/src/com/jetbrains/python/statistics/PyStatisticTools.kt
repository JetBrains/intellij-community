// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.asSafely
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.extensions.getSdk
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.isVirtualEnv
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.pipenv.isPipEnv
import com.jetbrains.python.sdk.poetry.isPoetry
import com.jetbrains.python.sdk.uv.isUv
import com.jetbrains.python.statistics.InterpreterCreationMode.*
import com.jetbrains.python.statistics.InterpreterTarget.*
import com.jetbrains.python.statistics.InterpreterType.*
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.venvReader.VirtualEnvReader

val Project.modules: Array<Module> get() = ModuleManager.getInstance(this).modules
val Project.sdks: List<Sdk> get() = modules.mapNotNull(Module::getSdk)

/**
 * Adds python language and interpreter version if the module has sdk
 */
fun getPythonSpecificInfo(module: Module): List<EventPair<*>> =
  module.getSdk()?.let { sdk -> getPythonSpecificInfo(sdk) } ?: emptyList()

/**
 * Adds python language and interpreter version
 */
fun getPythonSpecificInfo(sdk: Sdk): List<EventPair<*>> {
  val data = ArrayList<EventPair<*>>()
  if (!PythonSdkUtil.isPythonSdk(sdk)) return data
  data.add(EventFields.Language.with(PythonLanguage.INSTANCE))
  data.add(PYTHON_VERSION.with(sdk.version.toPythonVersion()))
  data.add(PYTHON_IMPLEMENTATION.with(sdk.pythonImplementation))
  data.add(EXECUTION_TYPE.with(sdk.executionType.value))
  data.add(INTERPRETER_TYPE.with(sdk.interpreterType.value))
  return data
}

@Deprecated("""
  It makes no sense to add a Python version or something similar to the event.
  If you need to get an event with a specific execution type, interpreter type, or whatsoever, please use the corresponding segment in the analytics platform.
  Thank you very much!
  """)
fun registerPythonSpecificEvent(group: EventLogGroup, eventId: String, vararg extraFields: EventField<*>): VarargEventId {
  return group.registerVarargEvent(eventId,
                                   EventFields.Language,
                                   PYTHON_VERSION,
                                   PYTHON_IMPLEMENTATION,
                                   EXECUTION_TYPE,
                                   INTERPRETER_TYPE,
                                   *extraFields)
}

val PYTHON_VERSION: StringEventField = EventFields.StringValidatedByRegexpReference("python_version", "version")
val PYTHON_IMPLEMENTATION: StringEventField = EventFields.String("python_implementation", listOf("Python"))


enum class InterpreterTarget(val value: String) {
  LOCAL("local"),
  REMOTE_DOCKER("Remote_Docker"),
  REMOTE_DOCKER_COMPOSE("Remote_Docker_Compose"),
  REMOTE_WSL("Remote_WSL"),
  REMOTE_NULL("Remote_null"),
  THIRD_PARTY("third_party"),
  REMOTE_SSH_CREDENTIALS("Remote_SSH_Credentials"),
  REMOTE_VAGRANT("Remote_Vagrant"),
  REMOTE_WEB_DEPLOYMENT("Remote_Web_Deployment"),
  REMOTE_UNKNOWN("Remote_Unknown"),

  TARGET_SSH_WEB_DEVELOPMENT("ssh/web-deployment"),
  TARGET_SSH_SFTP("ssh/sftp"),
  TARGET_DOCKER("docker"),
  TARGET_DOCKER_COMPOSE("docker-compose"),
  TARGET_VAGRANT("vagrant"),
  TARGET_WSL("wsl"),
}

val EXECUTION_TYPE: StringEventField = EventFields.String("executionType", listOf(
  LOCAL.value,
  REMOTE_DOCKER.value,
  REMOTE_DOCKER_COMPOSE.value,
  REMOTE_WSL.value,
  REMOTE_NULL.value,
  THIRD_PARTY.value,
  REMOTE_SSH_CREDENTIALS.value,
  REMOTE_VAGRANT.value,
  REMOTE_WEB_DEPLOYMENT.value,
  REMOTE_UNKNOWN.value))

enum class InterpreterType(val value: String) {
  PIPENV("pipenv"),
  CONDAVENV("condavenv"),
  BASE_CONDA("base_conda"),
  VIRTUALENV("virtualenv"),
  REGULAR("regular"),
  POETRY("poetry"),
  PYENV("pyenv"),
  UV("uv"),
  HATCH("hatch"),
}

enum class InterpreterCreationMode(val value: String) {
  SIMPLE("simple"),
  CUSTOM("custom"),
  NA("not_applicable"),
}

val INTERPRETER_TYPE: StringEventField = EventFields.String("interpreterType", InterpreterType.entries.map { it.value })

val INTERPRETER_CREATION_MODE: StringEventField = EventFields.String(
  "interpreter_creation_mode",
  listOf(SIMPLE.value, CUSTOM.value, NA.value)
)

internal val PREVIOUSLY_CONFIGURED: BooleanEventField = EventFields.Boolean("previously_configured")


private val Sdk.pythonImplementation: String get() = PythonSdkFlavor.getFlavor(this)?.name ?: "Python"
val Sdk?.version: LanguageLevel get() = PySdkUtil.getLanguageLevelForSdk(this)
val Sdk.executionType: InterpreterTarget
  get() =
    when (val additionalData = sdkAdditionalData) {
      is PyTargetAwareAdditionalData -> additionalData.executionType
      is PyRemoteSdkAdditionalDataBase -> additionalData.executionType
      else -> LOCAL
    }

val Sdk.interpreterType: InterpreterType
  get() = when {
    // The order of checks is important here since e.g. a pipenv is a virtualenv
    isPipEnv -> PIPENV
    isUv -> UV
    isPoetry -> POETRY
    this.isCondaVirtualEnv || this.sdkAdditionalData.asSafely<PythonSdkAdditionalData>()?.flavor is CondaEnvSdkFlavor -> CONDAVENV
    VirtualEnvReader.Instance.isPyenvSdk(getHomePath()) -> PYENV
    this.isVirtualEnv -> VIRTUALENV
    else -> REGULAR
  }

/**
 * Mapping from new targets to an old ones is need to keep compatibility with the current fus schema.
 * We are going to clean up these code together with housekeeping tasks about getting rid of old remotes.
 */
private val PyTargetAwareAdditionalData.executionType: InterpreterTarget
  get() =
    targetEnvironmentConfiguration?.typeId?.let { typeId ->
      when (typeId) {
        TARGET_SSH_WEB_DEVELOPMENT.value -> REMOTE_WEB_DEPLOYMENT
        TARGET_SSH_SFTP.value -> REMOTE_SSH_CREDENTIALS
        TARGET_DOCKER.value -> REMOTE_DOCKER
        TARGET_DOCKER_COMPOSE.value -> REMOTE_DOCKER_COMPOSE
        TARGET_VAGRANT.value -> REMOTE_VAGRANT
        TARGET_WSL.value -> REMOTE_WSL
        else -> REMOTE_UNKNOWN
      }
    } ?: REMOTE_UNKNOWN

private val PyRemoteSdkAdditionalDataBase.executionType: InterpreterTarget
  get() = remoteConnectionType.let { type ->
    when {
      type == null -> "Remote_null"
      getPluginInfo(type.javaClass).isDevelopedByJetBrains() -> "Remote_${type.name?.replace(' ', '_')}"
      else -> "third_party"
    }.let { name -> InterpreterTarget.values().firstOrNull { it.value == name } ?: REMOTE_UNKNOWN }
  }
