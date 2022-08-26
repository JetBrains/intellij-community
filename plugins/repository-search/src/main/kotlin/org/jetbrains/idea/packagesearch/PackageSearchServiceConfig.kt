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

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import org.jetbrains.idea.reposearch.PluginEnvironment

interface PackageSearchServiceConfig {
  val baseUrl: String

  val userAgent: String
    get() = ApplicationNamesInfo.getInstance().productName + "/" + ApplicationInfo.getInstance().fullVersion

  val headers: List<Pair<String, String>>
    get() = listOf(
      Pair("JB-Plugin-Version", PluginEnvironment.pluginVersion),
      Pair("JB-IDE-Version", PluginEnvironment.ideVersion)
    )

  val timeoutInSeconds: Int

  val useCache: Boolean
    get() = false

  val forceHttps: Boolean
    get() = true
}