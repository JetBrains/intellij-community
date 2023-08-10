// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.ext.remoteRun

import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.DigestUtil
import com.jetbrains.plugins.remotesdk.target.ssh.target.SshTargetEnvironmentConfiguration
import org.jetbrains.idea.maven.execution.target.MavenRuntimeTargetConfiguration
import org.jetbrains.idea.maven.execution.target.TargetConfigurationMavenExtension
import java.nio.file.Path

internal class MavenOnSshTargetConfigurationExtension : TargetConfigurationMavenExtension {
  override fun createUploadRoot(mavenRuntimeConfiguration: MavenRuntimeTargetConfiguration?,
                                targetEnvironmentRequest: TargetEnvironmentRequest,
                                volumeDescriptor: LanguageRuntimeType.VolumeDescriptor,
                                localRootPath: Path): TargetEnvironment.UploadRoot? {
    val targetEnvironmentConfiguration = targetEnvironmentRequest.configuration
    if (targetEnvironmentConfiguration !is SshTargetEnvironmentConfiguration) return null
    if (!targetEnvironmentConfiguration.useRsync) return null

    val applicationDir = targetEnvironmentConfiguration.projectRootOnTarget
    if (applicationDir.isBlank()) return null

    val fileSeparator = targetEnvironmentRequest.targetPlatform.platform.fileSeparator
    val targetRootParentDirName = localRootPath.sha256().take(16)
    val targetRootParentDir = applicationDir + fileSeparator + targetRootParentDirName
    val targetRootPath = TargetEnvironment.TargetPath.Temporary(volumeDescriptor.type.id, targetRootParentDir)
    return TargetEnvironment.UploadRoot(localRootPath, targetRootPath).also {
      it.volumeData = mavenRuntimeConfiguration?.getTargetSpecificData(volumeDescriptor)
    }
  }

  private fun Path.sha256(): String {
    val digest = DigestUtil.sha256()
    digest.update(this.toString().toByteArray())
    return StringUtil.toHexString(digest.digest())
  }
}