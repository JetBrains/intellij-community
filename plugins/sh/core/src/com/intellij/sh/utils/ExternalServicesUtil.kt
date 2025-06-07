package com.intellij.sh.utils

import com.intellij.openapi.application.PathManager
import com.intellij.platform.eel.*
import com.intellij.sh.ShLanguage
import java.nio.file.Path

internal object ExternalServicesUtil {
  @JvmStatic
  fun computeDownloadPath(eel: EelApi): Path {
    var basePath = Path.of(PathManager.getPluginsPath()).resolve(ShLanguage.INSTANCE.id)

    if (eel is LocalEelApi) {
      return basePath
    }

    val platform = eel.platform

    when {
      platform.isMac -> basePath = basePath.resolve("mac")
      platform.isLinux -> basePath = basePath.resolve("linux")
      platform.isWindows -> basePath = basePath.resolve("windows")
      platform.isFreeBSD -> basePath = basePath.resolve("freebsd")
    }

    when {
      platform.isX86_64 -> basePath = basePath.resolve("amd64")
      platform.isX86 -> basePath = basePath.resolve("i386")
      platform.isArm64 -> basePath = basePath.resolve("arm64")
    }

    return basePath
  }
}