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
import com.intellij.openapi.progress.ProgressManager
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.packagesearch.DefaultPackageServiceConfig
import org.jetbrains.idea.packagesearch.PackageSearchServiceConfig
import org.jetbrains.idea.reposearch.DependencySearchProvider
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage
import java.util.function.Consumer

/**
 * If you need to access Package Search API only, it is generally recommended to use [PackageSearchApiClient] directly.
 *
 * This class is needed to support the [DependencySearchProvider] interface, used by Maven plugin.
 */
class PackageSearchProvider(
  config: PackageSearchServiceConfig = service<DefaultPackageServiceConfig>()
) : DependencySearchProvider {

  private val myClient = PackageSearchApiClient(config)

  override fun fulltextSearch(searchString: String, consumer: Consumer<RepositoryArtifactData>) = runBlocking {
    ProgressManager.checkCanceled()
    val pkgsResponse = myClient.packagesByQuery(searchString)
    pkgsResponse.packages
      .filter { it.groupId.isNotBlank() && it.artifactId.isNotBlank() }
      .map { convertApiStandardPackage2RepositoryArtifactData(it) }
      .forEach { consumer.accept(it) }
  }

  override fun suggestPrefix(groupId: String?, artifactId: String?, consumer: Consumer<RepositoryArtifactData>) = runBlocking {
    ProgressManager.checkCanceled()
    val pkgsResponse = myClient.suggestPackages(groupId, artifactId)
    pkgsResponse.packages
      .filter { it.groupId.isNotBlank() && it.artifactId.isNotBlank() }
      .map { convertApiStandardPackage2RepositoryArtifactData(it) }
      .forEach { consumer.accept(it) }
  }

  override fun isLocal(): Boolean = false

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