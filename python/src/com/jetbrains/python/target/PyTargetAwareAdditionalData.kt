// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.target

import com.intellij.execution.target.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.remote.RemoteSdkProperties
import com.intellij.remote.RemoteSdkPropertiesHolder
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor
import org.jdom.Element
import java.nio.file.Path
import java.util.*

/**
 * Aims to replace [com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase].
 * For the transitional period of time the both of them are supposed to be
 * used.
 */
class PyTargetAwareAdditionalData private constructor(private val b: RemoteSdkPropertiesHolder,
                                                      flavorAndData: PyFlavorAndData<*, *>?,
                                                      targetEnvironmentConfiguration: TargetEnvironmentConfiguration? = null) : PythonSdkAdditionalData(
  flavorAndData),
                                                                                                                                TargetBasedSdkAdditionalData,
                                                                                                                                RemoteSdkProperties by b,
                                                                                                                                PyRemoteSdkAdditionalDataMarker {


  /**
   * The source of truth for the target configuration.
   */
  private var targetState: ContributedConfigurationsList.ContributedStateBase? = null

  /**
   * The backing field for [targetEnvironmentConfiguration].
   */
  private var _targetEnvironmentConfiguration: TargetEnvironmentConfiguration? = targetEnvironmentConfiguration

  /**
   * The target configuration.
   *
   * Note that [targetEnvironmentConfiguration] could be `null` even if [targetState] is not `null`, when there is no appropriate
   * [TargetEnvironmentType] available for deserializing and handling [ContributedStateBase.innerState] of [targetState].
   */
  override var targetEnvironmentConfiguration: TargetEnvironmentConfiguration?
    get() = _targetEnvironmentConfiguration
    set(value) {
      targetState = value?.let { ContributedConfigurationsList.ContributedStateBase().apply { loadFromConfiguration(value) } }
      _targetEnvironmentConfiguration = value
    }

  constructor(flavorAndData: PyFlavorAndData<*, *>, targetEnvironmentConfiguration: TargetEnvironmentConfiguration? = null) : this(
    RemoteSdkPropertiesHolder(DEFAULT_PYCHARM_HELPERS_DIR_NAME), flavorAndData, targetEnvironmentConfiguration)

  init {
    this.targetEnvironmentConfiguration = targetEnvironmentConfiguration
  }

  override fun save(rootElement: Element) {
    // store "interpeter paths" (i.e. `PYTHONPATH` elements)
    super.save(rootElement)
    // store `INTERPRETER_PATH`, `HELPERS_PATH`, etc
    b.save(rootElement)
    // store target configuration
    saveTargetBasedSdkAdditionalData(rootElement, targetState)
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
    val (loadedState, loadedConfiguration) = loadTargetBasedSdkAdditionalData(element)
    // add Python language runtime for the loaded configuration
    if (loadedConfiguration != null) {
      val pythonLanguageRuntimeConfiguration = PythonLanguageRuntimeConfiguration()
      interpreterPath?.let {
        pythonLanguageRuntimeConfiguration.pythonInterpreterPath = it
      }
      loadedConfiguration.addLanguageRuntime(pythonLanguageRuntimeConfiguration)
    }
    targetState = loadedState
    _targetEnvironmentConfiguration = loadedConfiguration

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

  private val Collection<VirtualFile>.asMappings get() = associate { it.toNioPath() to b.pathMappings.convertToRemote(it.path) }

  companion object {
    private const val DEFAULT_PYCHARM_HELPERS_DIR_NAME = ".pycharm_helpers"

    private val LOG = logger<PyTargetAwareAdditionalData>()

    /**
     * [local, remote] mapping for paths added by user
     */
    @JvmStatic
    val PyTargetAwareAdditionalData.pathsAddedByUser: Map<Path, String> get() = this.addedPathFiles.asMappings

    /**
     * [local, remote] mapping for paths explicitly removed by user
     */
    @JvmStatic
    val PyTargetAwareAdditionalData.pathsRemovedByUser: Map<Path, String> get() = this.excludedPathFiles.asMappings


    /**
     * Loads target data if it exists in xml. Returns `null` otherwise.
     * @see com.jetbrains.python.remote.PyRemoteSdkAdditionalData.loadRemote
     */
    @JvmStatic
    fun loadTargetAwareData(sdk: Sdk, element: Element): PyTargetAwareAdditionalData? {
      val homePath = sdk.homePath ?: throw IllegalStateException("Home path must not be null")
      // TODO Python flavor identifier must be stored in `element` and taken from it here
      val data = PyTargetAwareAdditionalData(flavorAndData = PyFlavorAndData(PyFlavorData.Empty, UnixPythonSdkFlavor.getInstance()))
      data.interpreterPath = homePath
      data.load(element)
      // TODO [targets] Load `SKELETONS_PATH` for Target-based Python SDK from `Element`
      // TODO [targets] Load `VERSION` for Target-based Python SDK from `Element`
      return if (data.targetEnvironmentConfiguration != null) data else null
    }
  }

  private sealed class DataOrFlavor
}