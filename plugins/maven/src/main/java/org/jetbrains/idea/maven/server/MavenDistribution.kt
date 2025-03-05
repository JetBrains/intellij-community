// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.idea.maven.utils.MavenUtil
import java.nio.file.Path

interface MavenDistribution {
  val name: String
  val mavenHome: Path
  val version: String?
  fun isValid(): Boolean
  fun compatibleWith(mavenDistribution: MavenDistribution): Boolean

}

class LocalMavenDistribution(override val mavenHome: Path, override val name: String) : MavenDistribution {
  override val version: String? by lazy {
    MavenUtil.getMavenVersion(mavenHome)
  }

  override fun compatibleWith(mavenDistribution: MavenDistribution): Boolean {
    return mavenDistribution == this || FileUtil.pathsEqual(mavenDistribution.mavenHome.toString(), mavenHome.toString())
  }

  override fun isValid() = version != null
  override fun toString(): String {
    return "$name($mavenHome) v $version"
  }
}
fun MavenDistribution.isMaven4(): Boolean {
  val v = this.version
  return v != null && v.startsWith("4.")
}