// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.io.path.pathString

sealed interface MavenHomeType {
  @get:NlsContexts.Label
  val title: String
  val description: String
}

/**
 * Represents a static resolved Maven Home.
 * Maven home considered as static if it could be resolved without knowledge of path to project
 * e.g. Bundled maven. In opposite, if Maven Home is set to wrapper we cannot determine exact maven home wihout knowledge of maven multimodule dir
 */
interface StaticResolvedMavenHomeType : MavenHomeType
object MavenWrapper : MavenHomeType {
  override val title = MavenProjectBundle.message("maven.wrapper.version.title")
  override val description = MavenProjectBundle.message("maven.wrapper.version.label")
}

object BundledMaven3 : StaticResolvedMavenHomeType {
  override val title = MavenProjectBundle.message("maven.bundled.version.3.title")
  override val description = MavenProjectBundle.message("maven.bundled.version.label")
}

object BundledMaven4 : StaticResolvedMavenHomeType {
  override val title = MavenProjectBundle.message("maven.bundled.version.4.title")
  override val description = MavenProjectBundle.message("maven.bundled.version.label")
}

data class MavenInSpecificPath(val mavenHome: String) : StaticResolvedMavenHomeType {
  constructor(home: Path) : this(home.toAbsolutePath().pathString)

  override val title = mavenHome
  override val description = MavenProjectBundle.message("maven.home.specific.label", mavenHome)
}


/**
 * for UI use only
 */
@Internal
fun resolveMavenHomeType(@NlsContexts.Label s: String?): MavenHomeType {
  return if (s.isNullOrBlank()) BundledMaven3
  else if (s == BundledMaven3.title) BundledMaven3
  else if (s == BundledMaven4.title) BundledMaven4
  else if (s == MavenWrapper.title) MavenWrapper
  else MavenInSpecificPath(s)
}

/**
 * Actully, most probably wrappered maven will have same settings as bundled.
 * This settings are used to resolve local repository, and we need conf/settings.xml.
 *
 * ATM we do not support rare exotic case when wrapper use private maven distribution with custom global config.
 */
fun MavenHomeType.staticOrBundled(): StaticResolvedMavenHomeType {
  return this as? StaticResolvedMavenHomeType ?: BundledMaven3
}

fun getAllKnownHomes(): List<MavenHomeType> =
  listOf(BundledMaven3, MavenWrapper)
