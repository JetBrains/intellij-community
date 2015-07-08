package org.jetbrains.settingsRepository

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.util.SystemInfo

val PROJECTS_DIR_NAME: String = "_projects/"

private fun getOsFolderName() = when {
  SystemInfo.isWindows -> "_windows"
  SystemInfo.isMac -> "_mac"
  SystemInfo.isLinux -> "_linux"
  SystemInfo.isFreeBSD -> "_freebsd"
  SystemInfo.isUnix -> "_unix"
  else -> "_unknown"
}

fun buildPath(path: String, roamingType: RoamingType, projectKey: String? = null) = when {
  projectKey != null -> "$PROJECTS_DIR_NAME$projectKey/$path"
  roamingType == RoamingType.PER_PLATFORM -> "${getOsFolderName()}/$path"
  else -> path
}