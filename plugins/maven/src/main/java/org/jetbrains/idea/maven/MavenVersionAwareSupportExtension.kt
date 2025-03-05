// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.maven.project.StaticResolvedMavenHomeType
import org.jetbrains.idea.maven.server.MavenDistribution
import java.io.File
import java.nio.file.Path

@ApiStatus.Internal
interface MavenVersionAwareSupportExtension {
  fun isSupportedByExtension(mavenHome: File): Boolean

  fun getMavenHomeFile(mavenHomeType: StaticResolvedMavenHomeType): Path?

  fun collectClassPathAndLibsFolder(distribution: MavenDistribution): List<Path>

  fun getMainClass(distribution: MavenDistribution): String {
    return DEFAULT_MAIN_CLASS
  }

  companion object {
    const val DEFAULT_MAIN_CLASS: @NonNls String = "org.jetbrains.idea.maven.server.RemoteMavenServer"

    val MAVEN_VERSION_SUPPORT: ExtensionPointName<MavenVersionAwareSupportExtension> = ExtensionPointName<MavenVersionAwareSupportExtension>("org.jetbrains.idea.maven.versionAwareMavenSupport")
  }
}
