// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.target

import com.intellij.execution.target.LanguageRuntimeConfiguration
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.extensions.ExtensionPointName
import java.nio.file.Path

class MavenRuntimeTargetConfiguration : LanguageRuntimeConfiguration(MavenRuntimeTypeConstants.TYPE_ID),
                                        PersistentStateComponent<MavenRuntimeTargetConfiguration.MyState> {
  var homePath: String = ""
  var versionString: String = ""

  override fun getState() = MyState().also {
    it.homePath = this.homePath
    it.versionString = this.versionString
  }

  override fun loadState(state: MyState) {
    this.homePath = state.homePath ?: ""
    this.versionString = state.versionString ?: ""
  }

  class MyState : BaseState() {
    var homePath by string()
    var versionString by string()
  }

  companion object {
    fun createUploadRoot(mavenRuntimeConfiguration: MavenRuntimeTargetConfiguration?,
                         targetEnvironmentRequest: TargetEnvironmentRequest,
                         volumeDescriptor: LanguageRuntimeType.VolumeDescriptor,
                         localRootPath: Path): TargetEnvironment.UploadRoot {
      return EP_NAME.computeSafeIfAny {
        it.createUploadRoot(mavenRuntimeConfiguration, targetEnvironmentRequest, volumeDescriptor, localRootPath)
      } ?: mavenRuntimeConfiguration?.createUploadRoot(volumeDescriptor, localRootPath)
             ?: TargetEnvironment.UploadRoot(localRootPath, TargetEnvironment.TargetPath.Temporary())
    }

    private val EP_NAME = ExtensionPointName.create<TargetConfigurationMavenExtension>("org.jetbrains.idea.maven.targetConfigurationExtension")
  }
}