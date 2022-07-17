import org.jetbrains.intellij.build.IdeaProjectLoaderUtil
import org.jetbrains.intellij.build.pycharm.PythonCommunityPluginBuilder

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
object PythonCommunityPluginBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass).toString()
    PythonCommunityPluginBuilder(communityHome).build()
  }
}