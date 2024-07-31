// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.FileSet
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import org.jetbrains.intellij.build.executeStep
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div


object PyCharmBuildUtils {
  const val SKELETONS_COPY_STEP = "skeletons_copy"
  @JvmStatic
  suspend fun copySkeletons(context: BuildContext, targetDirectory: Path, mask: String) {
    context.executeStep(TraceManager.spanBuilder("copying skeletons"), SKELETONS_COPY_STEP) {
      val skeletonsDir = context.paths.projectHome.resolve("skeletons")
      if (!TeamCityHelper.isUnderTeamCity && !Files.isDirectory(skeletonsDir)) {
        context.messages.warning("Skipping non-existent directory $skeletonsDir")
        return@executeStep
      }

      FileSet(skeletonsDir)
        .include(mask)
        .copyToDir(targetDirectory / "skeletons")
    }
  }
}