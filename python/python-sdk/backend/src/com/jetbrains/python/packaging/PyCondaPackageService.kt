// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemDependent
import java.nio.file.Path

@State(name = "PyCondaPackageService", storages = [Storage(value = "conda_packages.xml", roamingType = RoamingType.DISABLED)])
class PyCondaPackageService : PersistentStateComponent<PyCondaPackageService> {
  @Property
  var preferredCondaPath: @SystemDependent String? = null
    private set

  override fun getState(): PyCondaPackageService = this

  override fun loadState(state: PyCondaPackageService) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    private val LOG = Logger.getInstance(PyCondaPackageService::class.java)

    fun getInstance(): PyCondaPackageService = service<PyCondaPackageService>()

    @ApiStatus.Internal
    @JvmStatic
    fun getCondaExecutable(): Path? {
      val preferredCondaPath = getInstance().preferredCondaPath
      return if (!preferredCondaPath.isNullOrEmpty()) {
        LOG.info("Using $preferredCondaPath as a conda executable (specified as a preferred conda path)")
        Path.of(preferredCondaPath)
      }
      else getSystemCondaExecutable()
    }

    @Deprecated("Use getCondaExecutable(): Path? instead")
    @JvmStatic
    @Suppress("unused", "UNUSED_PARAMETER")
    fun getCondaExecutable(sdkPath: String?): @SystemDependent String? = getCondaExecutable()?.toString()

    fun onCondaEnvCreated(condaExecutable: @SystemDependent String) {
      getInstance().preferredCondaPath = condaExecutable
    }
  }
}