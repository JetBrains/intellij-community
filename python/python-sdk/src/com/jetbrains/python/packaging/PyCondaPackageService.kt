// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Property
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

    @JvmStatic
    fun getCondaExecutable(sdkPath: String?): @SystemDependent String? {
      if (sdkPath != null) {
        val condaPath = findCondaExecutableRelativeToEnv(Path.of(sdkPath))
        if (condaPath != null) {
          LOG.info("Using $condaPath as a conda executable for $sdkPath (found as a relative to the env)")
          return condaPath.toString()
        }
      }

      val preferredCondaPath = getInstance().preferredCondaPath
      if (!preferredCondaPath.isNullOrEmpty()) {
        val forSdkPath = if (sdkPath == null) "" else " for $sdkPath"
        LOG.info("Using $preferredCondaPath as a conda executable$forSdkPath (specified as a preferred conda path)")
        return preferredCondaPath
      }

      return getSystemCondaExecutable()?.toString()
    }

    fun onCondaEnvCreated(condaExecutable: @SystemDependent String) {
      getInstance().preferredCondaPath = condaExecutable
    }
  }
}