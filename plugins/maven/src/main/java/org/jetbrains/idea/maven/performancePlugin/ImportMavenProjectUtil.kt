@file:JvmName("ImportMavenProjectUtil")
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin

import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.JpsProjectLoadingManager
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil

@VisibleForTesting
internal fun importProject(project: Project) {
  return runBlockingMaybeCancellable {
    suspendCancellableCoroutine { continuation ->
      ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized {
        continuation.resumeWith(Result.success(Unit))
      }
    }

    suspendCancellableCoroutine { continuation ->
      MavenUtil.runWhenInitialized(project) {
        continuation.resumeWith(Result.success(Unit))
      }
    }

    suspendCancellableCoroutine { continuation ->
      JpsProjectLoadingManager.getInstance(project).jpsProjectLoaded {
        continuation.resumeWith(Result.success(Unit))
      }
    }

    val mavenManager = MavenProjectsManager.getInstance(project)
    if (!mavenManager.isMavenizedProject) {
      val files = mavenManager.collectAllAvailablePomFiles()
      mavenManager.addManagedFilesWithProfilesAndUpdate(files, MavenExplicitProfiles.NONE, null, null)
    }
    else {
      mavenManager.updateAllMavenProjects(MavenSyncSpec.FULL_EXPLICIT)
    }
  }
}
