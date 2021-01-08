// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.target

import com.intellij.execution.target.*
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.extensions.ExtensionPointName
import java.nio.file.Path

class MavenRuntimeTargetConfiguration : LanguageRuntimeConfiguration(MavenRuntimeType.TYPE_ID),
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
                         targetEnvironmentConfiguration: TargetEnvironmentConfiguration,
                         volumeDescriptor: LanguageRuntimeType.VolumeDescriptor,
                         localRootPath: Path): TargetEnvironment.UploadRoot {
      return EP_NAME.computeSafeIfAny {
        it.createUploadRoot(mavenRuntimeConfiguration, targetEnvironmentRequest, targetEnvironmentConfiguration, volumeDescriptor, localRootPath)
      } ?: mavenRuntimeConfiguration?.createUploadRoot(volumeDescriptor, localRootPath)
             ?: TargetEnvironment.UploadRoot(localRootPath, TargetEnvironment.TargetPath.Temporary())
    }

    private val EP_NAME = ExtensionPointName.create<TargetConfigurationMavenExtension>("org.jetbrains.idea.maven.targetConfigurationExtension")
  }
}