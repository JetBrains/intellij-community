// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.application.options.PathMacrosImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.impl.ProjectWidePathMacroContributor
import com.intellij.openapi.progress.util.awaitWithCheckCanceled
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.EelProviderUtil
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.idea.maven.utils.MavenUtil.resolveDefaultLocalRepositoryForJpsMacros
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Maven home path depends on an environment where the project is located.
 * On one hand, we have an application-wide macros in `path.macros.xml`. The data from these macros is inapplicable to non-local projects,
 * such as WSL and Docker based. Here we decide the location by project.
 */
internal class MavenProjectPathMacroContributor : ProjectWidePathMacroContributor {
  private val repositoryByDescriptor = ConcurrentHashMap<EelDescriptor, String>()
  private val computationByDescriptor = ConcurrentHashMap<EelDescriptor, CompletableFuture<String>>()

  override fun getProjectPathMacros(projectFilePath: @SystemIndependent String): Map<String, String> {
    return mapOf(PathMacrosImpl.MAVEN_REPOSITORY to getPathToDefaultMavenLocalRepositoryOnSpecificEnv(projectFilePath))
  }

  fun getPathToDefaultMavenLocalRepositoryOnSpecificEnv(projectFilePath: @SystemIndependent String): String {
    val descriptor = EelProviderUtil.getEelDescriptor(Path.of(projectFilePath))
    val cached = repositoryByDescriptor[descriptor]
    if (cached != null) return cached
    // Always run the IO on a background thread. awaitWithCheckCanceled() polls the future while calling
    // checkCanceled(), so a NonBlockingReadAction releases its read-action lock promptly when a write
    // action is pending, then retries once the background IO has stored the result in the cache.
    val future = CompletableFuture<String>()
    val existing = computationByDescriptor.putIfAbsent(descriptor, future)
    if (existing != null) return existing.awaitWithCheckCanceled()
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val result = resolveDefaultLocalRepositoryForJpsMacros(descriptor).toAbsolutePath().toString()
        repositoryByDescriptor[descriptor] = result
        computationByDescriptor.remove(descriptor, future)
        future.complete(result)
      }
      catch (e: Throwable) {
        computationByDescriptor.remove(descriptor, future)
        future.completeExceptionally(e)
      }
    }
    return future.awaitWithCheckCanceled()
  }
}
