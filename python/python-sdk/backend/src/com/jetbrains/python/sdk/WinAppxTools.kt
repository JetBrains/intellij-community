// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.WindowsReparsePoint
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

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
 * There is no Java API to see reparse point destination, so this tool reads the point with the FFM API.
 * This tool returns AppX name (either ``PythonSoftwareFoundation...`` or ``DesktopAppInstaller..``).
 * We use it to check if ``python.exe`` is real python or WindowsStore mock.
 *
 * See [WinAppxTest]
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

@ApiStatus.Internal
fun getAppxFiles(expectedProduct: String?, filePattern: Regex): Collection<Path> {
  val folder = userAppxFolder ?: return emptyList()
  val entries = try {
    folder.listDirectoryEntries()
  }
  catch (e: IOException) {
    appxFilesLogger.info("Cannot list AppX folder $folder", e)
    return emptyList()
  }
  return entries
    .filter { filePattern.matches(it.name) }
    .sortedBy { it.nameWithoutExtension }
    .mapNotNull { file -> file.appxProduct?.let { product -> Pair(product, file) } }
    .toMap()
    .filterKeys { expectedProduct == null || expectedProduct in it }
    .values
}

private val appxFilesLogger = Logger.getInstance("com.jetbrains.python.sdk.WinAppxTools")


/**
 * If file is AppX reparse point link -- return its product name
 */
@get:ApiStatus.Internal
val Path.appxProduct: String?
  get() {
    val userAppxFolder = userAppxFolder ?: return null
    if (!this.startsWith(userAppxFolder)) return null
    return getAppxTag(this)?.let {
      if (storeMarker !in it) it else null
    }
  }


/**
 * Path to ``%LOCALAPPDATA%\Microsoft\WindowsApps``
 */
private val userAppxFolder: Path? =
  if (!SystemInfo.isWin10OrNewer) {
    null
  }
  else {
    System.getenv("LOCALAPPDATA")?.let { localappdata ->
      val appsPath = Path.of(localappdata, "Microsoft//WindowsApps")
      if (appsPath.exists()) appsPath else null
    }
  }


// https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-fscc/c8e77b37-3909-4fe6-a4ea-2b9d423b1ee4

/** The only `AppExecLink` payload version that Windows writes today. */
private const val appExecLinkVersion = 3

/**
 *  AppX apps are installed in "C:\Program Files\WindowsApps\".
You can't run them directly. Instead, you must use reparse point from
"%LOCALAPPDATA%\Microsoft\WindowsApps" (this folder is under the %PATH%)

Reparse point is the special structure on NTFS level that stores "reparse tag" (type) and some type-specific data.
When a user accesses such files, Windows redirects the request to the appropriate target.
So, files in "%LOCALAPPDATA%\Microsoft\WindowsApps" are reparse points to AppX apps, and AppX can only be launched via them.

But for Python, there can be a reparse point that points to Windows store, so Store is opened when Python is not installed.
There is no official way to tell if "python.exe" points to AppX python or AppX "Windows Store".

This tool reads reparse point info and tries to fetch AppX name, so we can see if it points to Store or not.
See https://youtrack.jetbrains.com/issue/PY-43082
 */
private fun getAppxTag(path: Path): String? {
  if (!SystemInfo.isWin10OrNewer) return null

  val reparsePoint = WindowsReparsePoint.read(path).getOrElse { failure ->
    appxFilesLogger.debug { "Cannot read the reparse point of $path: $failure" }
    return null
  }
  if (reparsePoint.tag != WindowsReparsePoint.IO_REPARSE_TAG_APPEXECLINK) {
    appxFilesLogger.debug { "$path has the tag 0x${Integer.toHexString(reparsePoint.tag)}, not an AppExecLink" }
    return null
  }
  return parseAppExecLink(reparsePoint.data)
}

/**
 * Reads the package family name from an `IO_REPARSE_TAG_APPEXECLINK` payload.
 *
 * The payload starts with a `ULONG` version, which is [appExecLinkVersion] today. A list of UTF-16LE strings
 * follows it, and a null character ends each string. The order is the package family name, the application user
 * model id, the target executable and the application type.
 *
 * @param payload the reparse data that follows the `REPARSE_DATA_BUFFER` header
 * @return the package family name, for example `PythonSoftwareFoundation.Python.3.12_qbz5n2kfra8p0`, or `null`
 * when the payload holds no name
 */
@VisibleForTesting
internal fun parseAppExecLink(payload: ByteArray): String? {
  if (payload.size < Int.SIZE_BYTES + Char.SIZE_BYTES) {
    appxFilesLogger.debug { "The AppExecLink payload holds ${payload.size} bytes, which is too few" }
    return null
  }

  val version = ByteBuffer.wrap(payload, 0, Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).int
  if (version != appExecLinkVersion) {
    // A later version still starts with the same string list, so read the name and only report the difference.
    appxFilesLogger.info("The AppExecLink payload has the version $version, not $appExecLinkVersion")
  }

  // One character takes two bytes, so drop a trailing odd byte before the decode.
  val textLength = (payload.size - Int.SIZE_BYTES) and 1.inv()
  val name = String(payload, Int.SIZE_BYTES, textLength, Charsets.UTF_16LE).substringBefore('\u0000')
  return name.ifEmpty { null }
}
