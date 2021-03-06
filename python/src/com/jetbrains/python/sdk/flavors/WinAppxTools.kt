// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.io.FilenameFilter
import java.util.concurrent.TimeUnit

/**
 * AppX packages installed to AppX volume (see ``Get-AppxDefaultVolume``, ``Get-AppxPackage``).
 * They can't be executed nor read.
 *
 * At the same time, **reparse point** is created somewhere in `%LOCALAPPDATA%`.
 * This point has tag ``IO_REPARSE_TAG_APPEXECLINK`` and it also added to `PATH`
 *
 * Their attributes are also inaccessible. [File#exists] returns false.
 * But when executed, they are processed by NTFS filter and redirected to their real location in AppX volume.
 *
 * There may be ``python.exe`` there, but it may point to Windows Store (so it can be installed when accessed) or to the real python.
 * There is no Java API to see reparse point destination, so we use native app (See ``appxreparse.c`` and README in the same folder).
 * This tool returns AppX name (either ``PythonSoftwareFoundation...`` or ``DesktopAppInstaller..``).
 * We use it to check if ``python.exe`` is real python or WindowsStore mock.
 */

/**
 * When product of AppX reparse point contains this word, that means it is a link to the store
 */
private const val storeMarker = "DesktopAppInstaller"

/**
 * Files in [userAppxFolder] that matches [filePattern] and contains [expectedProduct]] in their product name.
 * For example: ``PythonSoftwareFoundation.Python.3.8_qbz5n2kfra8p0``.
 * There may be several files linked to this product, we need only first.
 * And for 3.7 there could be ``PythonSoftwareFoundation.Python.3.7_(SOME_OTHER_UID)``.
 */
fun getAppxFiles(expectedProduct: String, filePattern: Regex): Collection<File> =
  userAppxFolder?.listFiles(FilenameFilter { _, name -> filePattern.matches(name) })
    ?.sortedBy { it.nameWithoutExtension }
    ?.mapNotNull { file -> file.appxProduct?.let { product -> Pair(product, file) } }
    ?.toMap()
    ?.filterKeys { expectedProduct in it }
    ?.values ?: emptyList()

/**
 * If file is AppX reparse point link -- return it's product name
 */
val File.appxProduct: String?
  get() {
    if (parentFile?.equals(userAppxFolder) != true) return null
    val reparseTool = PathManager.findBinFile("AppxReparse.exe")!!
    // Intellij API prohibits running external processes under Read action or on EDT, so we use java api as it is done for registry
    val process = Runtime.getRuntime().exec(arrayOf(reparseTool.toFile().absolutePath, absolutePath))
    // It is much faster in most cases, but since this code may run under EDT we limit it
    if (!process.waitFor(1, TimeUnit.SECONDS)) return null
    if (process.exitValue() != 0) return null
    // appxreparse outputs wide chars (2 bytes), they are LE on Intel
    val output = process.inputStream.readBytes().toString(Charsets.UTF_16LE)
    return if (storeMarker !in output) output else null
  }

/**
 * Path to ``%LOCALAPPDATA%\Microsoft\WindowsApps``
 */
private val userAppxFolder =
  if (!SystemInfo.isWin10OrNewer) {
    null
  }
  else {
    System.getenv("LOCALAPPDATA")?.let { localappdata ->
      val appsPath = File(localappdata, "Microsoft//WindowsApps")
      if (appsPath.exists()) appsPath else null
    }
  }
