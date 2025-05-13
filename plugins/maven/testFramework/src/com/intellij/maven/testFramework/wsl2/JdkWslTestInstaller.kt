// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.wsl2

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.projectRoots.impl.jdkDownloader.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.utils.MavenLog
import java.nio.file.Path
import kotlin.io.path.exists

@ApiStatus.Internal
class JdkWslTestInstaller(val path: Path, val jdkItem: JdkItem) {
  fun checkOrInstallJDK() {
    if (path.resolve("bin/java").exists()) {
      MavenLog.LOG.info("JDK is intalled in $path, will do nothing")
      return
    }
    val indicator =  EmptyProgressIndicator();
    ProgressManager.getInstance().runProcess({
                                               JdkInstaller.getInstance().installJdk(
                                                 JdkInstallRequestInfo(jdkItem, path),
                                                 indicator,
                                                 null
                                               )
                                             }, indicator)
  }

  companion object {
    fun readJdkItem(path: Path): JdkItem {
      return ReadJdkItemsForWSL.readJdkItems(path)[0]
    }
  }
}