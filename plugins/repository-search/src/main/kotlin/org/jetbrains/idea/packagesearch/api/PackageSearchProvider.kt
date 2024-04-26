/*
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.idea.packagesearch.api

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.packagesearch.DefaultPackageServiceConfig
import org.jetbrains.idea.packagesearch.PackageSearchServiceConfig
import org.jetbrains.idea.reposearch.DependencySearchProvider
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage

/**
 * If you need to access Package Search API only, it is generally recommended to use [PackageSearchApiClient] or [AsyncPackageSearchApiClient] directly.
 *
 * This class is needed to support the [DependencySearchProvider] interface, used by Maven plugin.
 */
class PackageSearchProvider(
  private val scope: CoroutineScope,
  config: PackageSearchServiceConfig = service<DefaultPackageServiceConfig>(),
  engine: HttpClientEngine? = null
) : DependencySearchProvider {

  private val myClient = PackageSearchApiClient(config, engine)
    .also { client -> scope.coroutineContext[Job]?.invokeOnCompletion { client.close() } }

  override suspend fun fulltextSearch(searchString: String): List<RepositoryArtifactData> {
    return myClient.packagesByQuery(searchString)
      .packages
      .filter { it.groupId.isNotBlank() && it.artifactId.isNotBlank() }
      .map { convertApiStandardPackage2RepositoryArtifactData(it) }
  }

  override suspend fun suggestPrefix(groupId: String, artifactId: String): List<RepositoryArtifactData> {
    return myClient.suggestPackages(groupId, artifactId)
      .packages
      .filter { it.groupId.isNotBlank() && it.artifactId.isNotBlank() }
      .map { convertApiStandardPackage2RepositoryArtifactData(it) }
  }

  override fun isLocal(): Boolean = false
  override val cacheKey = "PackageSearchProvider" // assuming there's only one PackageSearchProvider per project

  private fun convertApiStandardPackage2RepositoryArtifactData(pkg: ApiStandardPackage): RepositoryArtifactData {
    val items = pkg.versions.map {
      MavenDependencyCompletionItem(
        pkg.groupId,
        pkg.artifactId,
        it.version,
        MavenDependencyCompletionItem.Type.REMOTE)
    }
    return MavenRepositoryArtifactInfo(
      pkg.groupId,
      pkg.artifactId,
      items.toTypedArray()
    )
  }
}

fun PackageSearchProvider(
  project: Project,
  config: PackageSearchServiceConfig = service<DefaultPackageServiceConfig>(),
  engine: HttpClientEngine? = null
): PackageSearchProvider = PackageSearchProvider(project.service<LifecycleScope>().cs, config, engine)