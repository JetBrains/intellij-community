// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.target

import com.intellij.execution.target.ContributedConfigurationsList
import com.intellij.execution.target.TargetBasedSdkAdditionalData
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.hasTargetConfiguration
import com.intellij.execution.target.loadTargetConfiguration
import com.intellij.execution.target.saveTargetBasedSdkAdditionalData
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.remote.RemoteSdkProperties
import com.intellij.remote.RemoteSdkPropertiesHolder
import com.jetbrains.python.remote.PyRemoteSdkAdditionalData
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Aims to replace [com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase].
 * For the transitional period, both of them are supposed to be used.
 */
open class PyTargetAwareAdditionalData private constructor(
  private val b: RemoteSdkPropertiesHolder,
  flavorAndData: PyFlavorAndData<*, *>?,
  targetEnvironmentConfiguration: TargetEnvironmentConfiguration? = null,
) : PythonSdkAdditionalData(flavorAndData), TargetBasedSdkAdditionalData, RemoteSdkProperties by b, PyRemoteSdkAdditionalDataMarker {
  /**
   * The source of truth for the target configuration.
   */
  private var targetState: ContributedConfigurationsList.ContributedStateBase? = null

  /**
   * The target configuration.
   *
   * Note that [targetEnvironmentConfiguration] could be `null` even if [targetState] is not `null`, when there is no appropriate
   * [TargetEnvironmentType] available for deserializing and handling [ContributedConfigurationsList.ContributedStateBase.innerState] of [targetState].
   */
  override var targetEnvironmentConfiguration: TargetEnvironmentConfiguration? = targetEnvironmentConfiguration
    set(value) {
      field = value
      notifyTargetEnvironmentConfigurationChanged()
    }

  init {
    notifyTargetEnvironmentConfigurationChanged()
  }

  constructor(flavorAndData: PyFlavorAndData<*, *>, targetEnvironmentConfiguration: TargetEnvironmentConfiguration? = null) : this(RemoteSdkPropertiesHolder(PyRemoteSdkAdditionalData.PYCHARM_HELPERS), flavorAndData, targetEnvironmentConfiguration)

  override fun save(rootElement: Element) { // store "interpreter paths" (i.e. `PYTHONPATH` elements)
    super.save(rootElement) // store `INTERPRETER_PATH`, `HELPERS_PATH`, etc
    b.save(rootElement) // store target configuration
    saveTargetBasedSdkAdditionalData(rootElement, targetState)
  }

  override fun load(element: Element?) { // load "interpeter paths" (i.e. `PYTHONPATH` elements)
    if (element == null) {
      LOG.debug("Python SDK additional data XML element with target configuration properties is missing")
      return
    } // clear the state and environment configuration
    targetEnvironmentConfiguration = null

    super.load(element) // load `INTERPRETER_PATH`, `HELPERS_PATH`, etc
    b.load(element) // the state that contains information of the target, as for now the target configuration is embedded into the additional data
    val loadedConfiguration = loadTargetConfiguration(element) // add Python language runtime for the loaded configuration
    if (loadedConfiguration != null) {
      val pythonLanguageRuntimeConfiguration = PythonLanguageRuntimeConfiguration()
      interpreterPath?.let {
        pythonLanguageRuntimeConfiguration.pythonInterpreterPath = it
      }
      loadedConfiguration.addLanguageRuntime(pythonLanguageRuntimeConfiguration)
    }

    targetEnvironmentConfiguration = loadedConfiguration
  }

  /**
   * Updates [targetState] from [targetEnvironmentConfiguration].
   *
   * [TargetEnvironmentConfiguration] might have mutable fields and [targetState] should be properly updated on commit/apply actions.
   *
   * @see com.jetbrains.python.configuration.PythonTargetInterpreterDetailsConfigurable.apply
   * @see com.intellij.docker.remote.compose.target.DockerComposeTargetEnvironmentConfiguration
   */
  @ApiStatus.Internal
  fun notifyTargetEnvironmentConfigurationChanged() {
    targetState = targetEnvironmentConfiguration?.let { notNullTargetEnvironmentConfiguration ->
      ContributedConfigurationsList.ContributedStateBase().apply {
        loadFromConfiguration(notNullTargetEnvironmentConfiguration)
      }
    }
  }

  /**
   * @see com.jetbrains.python.remote.PyRemoteSdkAdditionalData.setSdkId
   */
  override fun setSdkId(sdkId: String?): Unit = throw IllegalStateException("sdkId in this class is constructed based on fields, so it can't be set")

  /**
   * @see com.jetbrains.python.remote.PyRemoteSdkAdditionalData.getSdkId
   */ // TODO [targets] Review the usages and probably deprecate this property as it does not seem to be sensible
  override fun getSdkId(): String = targetEnvironmentConfiguration?.displayName + interpreterPath

  private val Collection<VirtualFile>.asMappings get() = associate { it.toNioPath() to b.pathMappings.convertToRemote(it.path) }

  companion object {
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
      if (!hasTargetConfiguration(element)) {
        return null
      }

      // TODO Python flavor identifier must be stored in `element` and taken from it here
      val data = PyTargetAwareAdditionalData(flavorAndData = PyFlavorAndData(PyFlavorData.Empty, UnixPythonSdkFlavor.getInstance()))
      data.interpreterPath = homePath
      data.load(element)
      // TODO [targets] Load `SKELETONS_PATH` for Target-based Python SDK from `Element`
      // TODO [targets] Load `VERSION` for Target-based Python SDK from `Element`
      if (data.targetEnvironmentConfiguration == null) {
        return DummyTargetAwareAdditionalData(element)
      }

      return data
    }

    private class DummyTargetAwareAdditionalData(base: Element)
      : PyTargetAwareAdditionalData(flavorAndData = PyFlavorAndData(PyFlavorData.Empty, UnixPythonSdkFlavor.getInstance()))
      , PyRemoteSdkAdditionalDataMarker
    {
      val element: Element = base.clone()

      override fun save(rootElement: Element) {
        rootElement.setContent(element.children.map { it.clone() })
        rootElement.attributes.addAll(element.attributes.map { it.clone() })
      }
    }
  }
}
