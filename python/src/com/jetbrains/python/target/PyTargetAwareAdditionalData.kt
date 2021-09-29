// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.target

import com.intellij.configurationStore.ComponentSerializationUtil
import com.intellij.configurationStore.deserializeState
import com.intellij.execution.target.ContributedConfigurationsList
import com.intellij.execution.target.ContributedConfigurationsList.Companion.getSerializer
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.remote.RemoteSdkProperties
import com.intellij.remote.RemoteSdkPropertiesHolder
import com.intellij.util.xmlb.XmlSerializer
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import org.jdom.Element

/**
 * Aims to replace [com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase].
 * For the transitional period of time the both of them are supposed to be
 * used.
 */
class PyTargetAwareAdditionalData private constructor(private val b: RemoteSdkPropertiesHolder,
                                                      flavor: PythonSdkFlavor?) : PythonSdkAdditionalData(flavor),
                                                                                  RemoteSdkProperties by b,
                                                                                  PyRemoteSdkAdditionalDataMarker {
  /**
   * The source of truth for the target configuration.
   */
  private var targetState: ContributedConfigurationsList.ContributedStateBase? = null

  /**
   * The backing field for [targetEnvironmentConfiguration].
   */
  private var _targetEnvironmentConfiguration: TargetEnvironmentConfiguration? = null

  /**
   * The target configuration.
   *
   * Note that [targetEnvironmentConfiguration] could be `null` even if [targetState] is not `null`, when there is no appropriate
   * [TargetEnvironmentType] available for deserializing and handling [ContributedStateBase.innerState] of [targetState].
   */
  var targetEnvironmentConfiguration: TargetEnvironmentConfiguration?
    get() = _targetEnvironmentConfiguration
    set(value) {
      targetState = value?.let { ContributedConfigurationsList.ContributedStateBase().apply { loadFromConfiguration(value) } }
      _targetEnvironmentConfiguration = value
    }

  constructor(flavor: PythonSdkFlavor?) : this(RemoteSdkPropertiesHolder(DEFAULT_PYCHARM_HELPERS_DIR_NAME), flavor)

  fun getTargetEnvironmentRequest(project: Project?): TargetEnvironmentRequest? {
    return targetEnvironmentConfiguration?.createEnvironmentRequest(project ?: ProjectManager.getInstance().defaultProject)
  }

  override fun save(rootElement: Element) {
    // store "interpeter paths" (i.e. `PYTHONPATH` elements)
    super.save(rootElement)
    // store `INTERPRETER_PATH`, `HELPERS_PATH`, etc
    b.save(rootElement)
    // store target configuration
    val targetStateElement = Element("targetEnvironmentConfiguration")
    rootElement.addContent(targetStateElement)
    targetState?.let { XmlSerializer.serializeInto(it, targetStateElement) }
  }

  override fun load(element: Element?) {
    // clear the state
    targetState = null
    _targetEnvironmentConfiguration = null
    // load "interpeter paths" (i.e. `PYTHONPATH` elements)
    super.load(element)
    if (element == null) {
      LOG.debug("Python SDK additional data XML element with target configuration properties is missing")
      return
    }
    // load `INTERPRETER_PATH`, `HELPERS_PATH`, etc
    b.load(element)
    // the state that contains information of the target, as for now the target configuration is embedded into the additional data
    val targetConfigurationElement = element.getChild("targetEnvironmentConfiguration")
    val newTargetState = deserializeState(targetConfigurationElement, ContributedConfigurationsList.ContributedStateBase::class.java, null)
    targetState = newTargetState
    if (newTargetState == null) {
      LOG.warn("Cannot deserialize Python SDK target configuration")
      return
    }
    fromOneState(newTargetState).let { loadedConfiguration ->
      if (loadedConfiguration == null) {
        LOG.info("Cannot load Python SDK target configuration from the ")
      }
      else {
        _targetEnvironmentConfiguration = loadedConfiguration
      }
    }
  }

  /**
   * @see com.intellij.execution.target.ContributedConfigurationsList.fromOneState
   */
  private fun fromOneState(state: ContributedConfigurationsList.ContributedStateBase): TargetEnvironmentConfiguration? {
    val type = TargetEnvironmentType.EXTENSION_NAME.extensionList.firstOrNull { it.id == state.typeId }
    val defaultConfig = type?.createDefaultConfig()
    return defaultConfig?.also {
      it.displayName = state.name ?: ""
      ComponentSerializationUtil.loadComponentState(it.getSerializer(), state.innerState)
    }
  }

  /**
   * @see com.jetbrains.python.remote.PyRemoteSdkAdditionalData.setSdkId
   */
  override fun setSdkId(sdkId: String?): Unit =
    throw IllegalStateException("sdkId in this class is constructed based on fields, so it can't be set")

  /**
   * @see com.jetbrains.python.remote.PyRemoteSdkAdditionalData.getSdkId
   */
  // TODO [targets] Review the usages and probably deprecate this property as it does not seem to be sensible
  override fun getSdkId(): String = targetEnvironmentConfiguration?.displayName + interpreterPath

  companion object {
    private const val DEFAULT_PYCHARM_HELPERS_DIR_NAME = ".pycharm_helpers"

    private val LOG = logger<PyTargetAwareAdditionalData>()

    /**
     * @see com.jetbrains.python.remote.PyRemoteSdkAdditionalData.loadRemote
     */
    @JvmStatic
    fun loadTargetAwareData(sdk: Sdk, element: Element): PyTargetAwareAdditionalData {
      val homePath = sdk.homePath ?: throw IllegalStateException("Home path must not be null")
      val data = PyTargetAwareAdditionalData(flavor = null)
      data.interpreterPath = homePath
      data.load(element)
      // TODO [targets] Load `SKELETONS_PATH` for Target-based Python SDK from `Element`
      // TODO [targets] Load `VERSION` for Target-based Python SDK from `Element`
      return data
    }
  }
}