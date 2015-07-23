package org.jetbrains.settingsRepository

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.util.SystemInfo

val PROJECTS_DIR_NAME: String = "_projects/"

private fun getOsFolderName() = when {
  SystemInfo.isMac -> "_mac"
  SystemInfo.isWindows -> "_windows"
  SystemInfo.isLinux -> "_linux"
  SystemInfo.isFreeBSD -> "_freebsd"
  SystemInfo.isUnix -> "_unix"
  else -> "_unknown"
}

fun buildPath(path: String, roamingType: RoamingType, projectKey: String? = null): String {
  fun String.osIfNeed() = if (roamingType == RoamingType.PER_OS) "${getOsFolderName()}/$this" else this

  return if (projectKey == null) path.osIfNeed() else "$PROJECTS_DIR_NAME$projectKey/$path"
}