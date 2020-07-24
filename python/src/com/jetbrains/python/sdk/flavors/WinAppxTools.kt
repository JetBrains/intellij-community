// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.FilenameFilter

/**
 * AppX packages installed to AppX volume (see `Get-AppxDefaultVolume`).
 * At the same time, **reparse point** is created somewhere in `%LOCALAPPDATA%`.
 * This point has tag `IO_REPARSE_TAG_APPEXECLINK` and it also added to `PATH`
 *
 * Such points can't be read. Their attributes are also inaccessible. [File#exists] returns false.
 * But when executed, they are processed by NTFS filter and redirected to their real location in AppX volume.
 * They are also returned with parent's [File#listFiles]
 * There is no Java API to fetch reparse data, and its structure is undocumented (although pretty simple), so we workaround it
 */

/**
 * If file is appx reparse point, then file.exists doesn't work.
 */
fun mayBeAppXReparsePoint(file: File): Boolean =
  pythonsStoreLocation?.let { storeLocation ->
    FileUtil.isAncestor(storeLocation, file, false)
  } == true

/**
 * Since you can't use file.exists for reparse point, this function checks if file exists
 */
fun appXReparsePointFileExists(file: File): Boolean {
  return file.exists() || (mayBeAppXReparsePoint(file) && file.parentFile.list()?.contains(file.name) == true)
}

/**
 * Appx apps are installed in [pythonsStoreLocation], each one in separate folder.
 * But folders are inaccessible, but there are reparse points on the toplevel.
 * This function provides list of them
 */
fun getAppXAppsInstalled(filenameFilter: FilenameFilter): List<File> =
  pythonsStoreLocation?.list(filenameFilter)?.mapNotNull { File(pythonsStoreLocation, it) }
  ?: emptyList()

private val pythonsStoreLocation
  get(): File? {
    if (!SystemInfo.isWin10OrNewer) {
      return null
    }
    val localappdata = System.getenv("LOCALAPPDATA") ?: return null
    val appsPath = File(localappdata, "Microsoft//WindowsApps")
    return if (appsPath.exists()) appsPath else null
  }



