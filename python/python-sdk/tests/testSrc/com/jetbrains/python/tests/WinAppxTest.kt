package com.jetbrains.python.tests

import com.intellij.openapi.util.SystemInfoRt
import com.jetbrains.python.sdk.appxProduct
import com.jetbrains.python.sdk.getAppxFiles
import com.jetbrains.python.sdk.parseAppExecLink
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class WinAppxTest {
  @Test
  fun testAppx() {
    Assumptions.assumeTrue(SystemInfoRt.isWindows, "Win only")
    val appxFiles = getAppxFiles(null, Regex(".+"))
    Assumptions.assumeTrue(appxFiles.isNotEmpty(), "No appx apps found")
    for (appxFile in appxFiles) {
      Assertions.assertNotNull(appxFile.appxProduct, "No appx product product for $appxFile")
    }
  }

  @Test
  fun testPayloadGivesThePackageFamilyName() {
    val payload = appExecLink(
      "PythonSoftwareFoundation.Python.3.12_qbz5n2kfra8p0",
      "PythonSoftwareFoundation.Python.3.12_qbz5n2kfra8p0!Python",
      """C:\Program Files\WindowsApps\python3.12.exe""",
      "0")
    Assertions.assertEquals("PythonSoftwareFoundation.Python.3.12_qbz5n2kfra8p0", parseAppExecLink(payload))
  }

  @Test
  fun testPayloadGivesTheStoreName() {
    val payload = appExecLink("Microsoft.DesktopAppInstaller_8wekyb3d8bbwe", "Microsoft.DesktopAppInstaller_8wekyb3d8bbwe!App")
    Assertions.assertEquals("Microsoft.DesktopAppInstaller_8wekyb3d8bbwe", parseAppExecLink(payload))
  }

  @Test
  fun testAnUnknownVersionStillParses() {
    val payload = appExecLink("Some.Package_abc", version = 4)
    Assertions.assertEquals("Some.Package_abc", parseAppExecLink(payload))
  }

  @Test
  fun testAShortPayloadGivesNull() {
    for (size in 0..5) {
      Assertions.assertNull(parseAppExecLink(ByteArray(size)), "A payload of $size bytes must give null")
    }
  }

  @Test
  fun testAnEmptyFirstStringGivesNull() {
    Assertions.assertNull(parseAppExecLink(appExecLink("", "Some.Package_abc!App")))
  }

  @Test
  fun testAnOddByteCountGivesTheName() {
    val payload = appExecLink("Some.Package_abc")
    Assertions.assertEquals("Some.Package_abc", parseAppExecLink(payload + 0x41.toByte()))
  }

  /**
   * Builds an `AppExecLink` payload: a little-endian `ULONG` version, then the strings as UTF-16LE. A null
   * character ends each string.
   */
  private fun appExecLink(vararg strings: String, version: Int = 3): ByteArray {
    val text = strings.joinToString(separator = "") { "$it\u0000" }.toByteArray(Charsets.UTF_16LE)
    val head = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(version).array()
    return head + text
  }
}
