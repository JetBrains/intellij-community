import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.pycharm.PyCharmCommunityProperties

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
object PyCharmCommunityInstallersBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass).toString()
    val context = BuildContext.createContext(
      communityHome,
      communityHome,
      PyCharmCommunityProperties(communityHome),
      ProprietaryBuildTools.DUMMY,
      BuildOptions()
    )
    BuildTasks.create(context).buildDistributions()
  }
}