// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.maven3

import com.intellij.openapi.roots.ui.distribution.AbstractDistributionInfo
import org.jetbrains.idea.maven.project.MavenConfigurableBundle

open class BundledDistributionInfo(version: String) : AbstractDistributionInfo() {
  override val name: String = MavenConfigurableBundle.message("maven.run.configuration.bundled.distribution.name", version)
  override val description: String = MavenConfigurableBundle.message("maven.run.configuration.bundled.distribution.description")
}

class Bundled3DistributionInfo(version: String?) : BundledDistributionInfo(version ?: "3")