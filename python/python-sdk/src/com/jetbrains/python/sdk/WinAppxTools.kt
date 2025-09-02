// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Kernel32.*
import com.sun.jna.platform.win32.Ntifs
import com.sun.jna.platform.win32.WinioctlUtil
import com.sun.jna.ptr.IntByReference
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.io.path.*

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
 * There is no Java API to see reparse point destination, so we use JNA.
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
fun getAppxFiles(expectedProduct: String?, filePattern: Regex): Collection<Path> =
  userAppxFolder?.listDirectoryEntries()
    ?.filter { filePattern.matches(it.name) }
    ?.sortedBy { it.nameWithoutExtension }
    ?.mapNotNull { file -> file.appxProduct?.let { product -> Pair(product, file) } }
    ?.toMap()
    ?.filterKeys { expectedProduct == null || expectedProduct in it }
    ?.values ?: emptyList()


/**
 * If file is AppX reparse point link -- return its product name
 */
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
private const val IO_REPARSE_TAG_APPEXECLINK = 0x8000001B

/**
 *  AppX apps are installed in "C:\Program Files\WindowsApps\".
You can't run them directly. Instead, you must use reparse point from
"%LOCALAPPDATA%\Microsoft\WindowsApps" (this folder is under the %PATH%)

Reparse point is the special structure on NTFS level that stores "reparse tag" (type) and some type-specific data.
When a user accesses such files, Windows redirects the request to the appropriate target.
So, files in "%LOCALAPPDATA%\Microsoft\WindowsApps" are reparse points to AppX apps, and AppX can only be launched via them.

But for Python, there can be a reparse point that points to Windows store, so Store is opened when Python is not installed.
There is no official way to tell if "python.exe" points to AppX python or AppX "Windows Store".

MS provides API (via DeviceIOControl) to read reparse point structure.
There is also a reparse point tag for "AppX link" in SDK.
Reparse data is undocumented, but it is just an array of wide chars with some unprintable chars at the beginning.

This tool reads reparse point info and tries to fetch AppX name, so we can see if it points to Store or not.
See https://youtrack.jetbrains.com/issue/PY-43082

Output is unicode 16-LE
 */
private fun getAppxTag(path: Path): String? {
  if (!SystemInfo.isWin10OrNewer) return null
  val kernel = INSTANCE
  val logger = Logger.getInstance(Kernel32::class.java)
  val file = kernel.CreateFile(path.pathString, GENERIC_READ, FILE_SHARE_READ, null, OPEN_EXISTING, FILE_FLAG_OPEN_REPARSE_POINT, null)
  if (file == INVALID_HANDLE_VALUE) {
    logger.warn("Invalid handle for $path")
    return null
  }
  val buffer = Ntifs.REPARSE_DATA_BUFFER()
  val bytesRead = IntByReference()
  if (!kernel.DeviceIoControl(file, WinioctlUtil.FSCTL_GET_REPARSE_POINT, null, 0, buffer.pointer, buffer.size(), bytesRead, null)) {
    logger.warn("DeviceIoControl error ${kernel.GetLastError()}")
    return null
  }
  if (bytesRead.value < 1) {
    logger.warn("0 bytes read")
    return null
  }
  buffer.read()
  if (buffer.ReparseTag != IO_REPARSE_TAG_APPEXECLINK.toInt()) {
    logger.warn("Wrong tag ${buffer.ReparseTag}")
    return null
  }
  // output is array of LE 2 bytes chars: \0\0\0[text]\0[junk]
  val charBuffer = Charsets.UTF_16LE.decode(ByteBuffer.wrap(buffer.u.genericReparseBuffer.DataBuffer))
  var from = 0
  var to = 0
  var startFound = false
  for ((i, char) in charBuffer.withIndex()) {
    val validChar = Character.getType(char) != Character.CONTROL.toInt()
    if (validChar && !startFound) {
      from = i
      startFound = true
    }
    if (!validChar && startFound) {
      to = i
      break
    }
  }
  return charBuffer.substring(from, to)
}