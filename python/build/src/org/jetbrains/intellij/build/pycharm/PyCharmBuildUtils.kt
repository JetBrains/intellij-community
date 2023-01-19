// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.FileSet
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div

object PyCharmBuildUtils {
  @JvmStatic
  fun copySkeletons(context: CompilationContext, targetDirectory: Path, mask: String) {
    val skeletonsDir = context.paths.projectHome.resolve("skeletons")
    if (!TeamCityHelper.isUnderTeamCity && !Files.isDirectory(skeletonsDir)) {
      context.messages.warning("Skipping non-existent directory $skeletonsDir")
      return
    }

    FileSet(skeletonsDir)
      .include(mask)
      .copyToDir(targetDirectory / "skeletons")
  }
}