/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.util.SystemInfo

internal const val PROJECTS_DIR_NAME: String = "_projects/"
private val osPrefixes = arrayOf("_mac/", "_windows/", "_linux/", "_freebsd/", "_unix/")

private fun getOsFolderName() = when {
  SystemInfo.isMac -> "_mac"
  SystemInfo.isWindows -> "_windows"
  SystemInfo.isLinux -> "_linux"
  SystemInfo.isFreeBSD -> "_freebsd"
  SystemInfo.isUnix -> "_unix"
  else -> "_unknown"
}

internal fun toRepositoryPath(path: String, roamingType: RoamingType, projectKey: String? = null): String {
  fun String.osIfNeed() = if (roamingType == RoamingType.PER_OS) "${getOsFolderName()}/$this" else this

  return if (projectKey == null) path.osIfNeed() else "$PROJECTS_DIR_NAME$projectKey/$path"
}

internal fun toIdeaPath(path: String): String {
  for (prefix in osPrefixes) {
    val result = path.removePrefix(prefix)
    if (result !== path) {
      return result
    }
  }
  return path
}