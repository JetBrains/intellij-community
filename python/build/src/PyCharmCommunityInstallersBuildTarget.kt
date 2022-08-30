// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.pycharm.PyCharmCommunityProperties

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
object PyCharmCommunityInstallersBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
    val context = BuildContextImpl.createContext(
      communityHome = communityHome,
      projectHome = communityHome.communityRoot,
      productProperties = PyCharmCommunityProperties(communityHome),
    )
    BuildTasks.create(context).buildDistributions()
  }
}