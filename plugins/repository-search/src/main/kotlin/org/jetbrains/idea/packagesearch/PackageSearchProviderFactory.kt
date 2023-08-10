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

package org.jetbrains.idea.packagesearch

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.RegistryManager
import org.jetbrains.idea.packagesearch.api.LifecycleScope
import org.jetbrains.idea.packagesearch.api.PackageSearchProvider
import org.jetbrains.idea.reposearch.DependencySearchProvider
import org.jetbrains.idea.reposearch.DependencySearchProvidersFactory

internal class PackageSearchProviderFactory : DependencySearchProvidersFactory {
  override fun getProviders(project: Project): Collection<DependencySearchProvider> {
    return if (!RegistryManager.getInstance().`is`("maven.packagesearch.enabled")) {
      emptyList()
    }
    else {
      setOf<DependencySearchProvider>(PackageSearchProvider(project))
    }
  }
}
