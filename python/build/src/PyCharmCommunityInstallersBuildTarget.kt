// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.build.BuildLifetime
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.pycharm.PyCharmBuildUtils
import org.jetbrains.intellij.build.pycharm.PyCharmCommunityProperties

object PyCharmCommunityInstallersBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    BuildLifetime().use { lifetime ->

      val options = BuildOptions().apply {
        // do not bother external users about clean/incremental
        // just remove out/ directory for clean build
        incrementalCompilation = true
        useCompiledClassesFromProjectOutput = false
        buildStepsToSkip += listOf(
          BuildOptions.MAC_SIGN_STEP,
          BuildOptions.WIN_SIGN_STEP,
          PyCharmBuildUtils.SKELETONS_COPY_STEP,
          BuildOptions.CROSS_PLATFORM_DISTRIBUTION_STEP,
          BuildOptions.WINDOWS_ZIP_STEP,
        )
      }
      val context = createBuildContext(
        projectHome = COMMUNITY_ROOT.communityRoot,
        productProperties = PyCharmCommunityProperties(COMMUNITY_ROOT.communityRoot),
        options = options, lifetime = lifetime,
      )
      buildDistributions(context)

    }
  }
}
