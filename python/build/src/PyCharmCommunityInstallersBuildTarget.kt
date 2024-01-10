// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.pycharm.PyCharmBuildUtils
import org.jetbrains.intellij.build.pycharm.PyCharmCommunityProperties

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
object PyCharmCommunityInstallersBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking(Dispatchers.Default) {
      val options = BuildOptions().apply {
        // we cannot provide consistent build number for PyCharm Community if it's built separately so use *.SNAPSHOT number to avoid confusion
        buildNumber = null

        // do not bother external users about clean/incremental
        // just remove out/ directory for clean build
        incrementalCompilation = true
        useCompiledClassesFromProjectOutput = false
        buildStepsToSkip.addAll(listOf(
          BuildOptions.MAC_SIGN_STEP,
          PyCharmBuildUtils.SKELETONS_COPY_STEP,
        ))
      }
      val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(PyCharmCommunityInstallersBuildTarget::class.java)
      val context = BuildContextImpl.createContext(
        communityHome = communityHome,
        projectHome = communityHome.communityRoot,
        productProperties = PyCharmCommunityProperties(communityHome.communityRoot),
        options = options,
      )
      BuildTasks.create(context).buildDistributions()
    }
  }
}