// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run

import com.intellij.platform.eel.*
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.utils.MavenLog

//https://learn.microsoft.com/en-us/windows/win32/intl/code-page-identifiers

private val WINDOWS_NON_UNICODE_CODEPAGES: Map<String, String> = mapOf(
  "037" to "Cp037",
  "437" to "Cp437",
  "500" to "Cp500",
  "737" to "Cp737",
  "775" to "Cp775",
  "850" to "Cp850",
  "852" to "Cp852",
  "855" to "Cp855",
  "857" to "Cp857",
  "858" to "Cp858",
  "860" to "Cp860",
  "861" to "Cp861",
  "862" to "Cp862",
  "863" to "Cp863",
  "864" to "Cp864",
  "865" to "Cp865",
  "866" to "Cp866",
  "869" to "Cp869",
  "870" to "Cp870",
  "874" to "x-windows-874",
  "875" to "Cp875",

  // East Asia
  "932" to "windows-31j",  // (Shift_JIS superset)
  "936" to "GBK",          // (Windows cp936)
  "949" to "x-windows-949", // (UHC/MS949)
  "950" to "Big5",
  "1361" to "x-Johab",

  // EBCDIC + Euro variants
  "1026" to "Cp1026",
  "1047" to "Cp1047",
  "1140" to "Cp1140",
  "1141" to "Cp1141",
  "1142" to "Cp1142",
  "1143" to "Cp1143",
  "1144" to "Cp1144",
  "1145" to "Cp1145",
  "1146" to "Cp1146",
  "1147" to "Cp1147",
  "1148" to "Cp1148",
  "1149" to "Cp1149",

  // Unicode
  "1200" to "UTF-16LE",
  "1201" to "UTF-16BE",
  "65001" to "UTF-8",

  // Windows-125x
  "1250" to "windows-1250",
  "1251" to "windows-1251",
  "1252" to "windows-1252",
  "1253" to "windows-1253",
  "1254" to "windows-1254",
  "1255" to "windows-1255",
  "1256" to "windows-1256",
  "1257" to "windows-1257",
  "1258" to "windows-1258",

  // ASCII
  "20127" to "US-ASCII",

  // KOI8
  "20866" to "KOI8-R",
  "21866" to "KOI8-U",


  // EUC / ISO-2022 / HZ
  "20932" to "EUC-JP",
  "50220" to "ISO-2022-JP",
  "50221" to "ISO-2022-JP",
  "50222" to "ISO-2022-JP",
  "50225" to "ISO-2022-KR",
  "50227" to "ISO-2022-CN",
  "51932" to "EUC-JP",
  "51936" to "EUC-CN",
  "51949" to "EUC-KR",
  "51950" to "EUC-TW",

  // ISO-8859 family
  "28591" to "ISO-8859-1",
  "28592" to "ISO-8859-2",
  "28593" to "ISO-8859-3",
  "28594" to "ISO-8859-4",
  "28595" to "ISO-8859-5",
  "28596" to "ISO-8859-6",
  "28597" to "ISO-8859-7",
  "28598" to "ISO-8859-8",
  "28599" to "ISO-8859-9",
  "28603" to "ISO-8859-13",
  "28605" to "ISO-8859-15",

  // Hebrew logical (Windows 38598)
  "38598" to "ISO-8859-8",

  // Mainland China (post-WinXP)
  "54936" to "GB18030"
)

@TestOnly
@ApiStatus.Internal
fun getAllWindowsCodePages(): Map<String, String> = WINDOWS_NON_UNICODE_CODEPAGES

@ThrowsChecked(ExecuteProcessException::class)
@ApiStatus.Internal
suspend fun EelExecApi.getCodepage(): String? {

  val existingEnv = fetchLoginShellEnvVariables()
  existingEnv["LC_ALL"].normalizeCharset()?.let {
    MavenLog.LOG.debug("extracted charset $it from LC_ALL env var")
    return it
  }
  existingEnv["LC_CTYPE"].normalizeCharset()?.let {
    MavenLog.LOG.debug("extracted charset $it from LC_CTYPE env var")
    return it
  }

  return when (this) {
    is EelExecPosixApi -> extractCodepageFromLocale(spawnProcess("locale").eelIt().awaitProcessResult())
    is EelExecWindowsApi -> extractCodepageFromChcp(spawnProcess("chcp.com").eelIt().awaitProcessResult())
  }

}

@ApiStatus.Internal
fun extractCodepageFromChcp(result: EelProcessExecutionResult): String? {
  if (result.exitCode != 0) {
    MavenLog.LOG.warn("chcp exit code is ${result.exitCode}, err: ${result.stderrString}, out: ${result.stdoutString}")
    return null
  }
  val stdout = result.stdoutString.trim()
  val key = stdout.reversed().takeWhile { it in '0'..'9' }.reversed()
  return WINDOWS_NON_UNICODE_CODEPAGES[key]
}

@ApiStatus.Internal
fun extractCodepageFromLocale(result: EelProcessExecutionResult): String? {
  if (result.exitCode != 0) {
    MavenLog.LOG.warn("locale exit code is ${result.exitCode}, err: ${result.stderrString}, out: ${result.stdoutString}")
    return null
  }
  val stdout = result.stdoutString.lines()
  extractPosix(stdout, "LC_ALL")?.let {
    MavenLog.LOG.debug("extracted charset $it from locale using LC_ALL")
    return it
  }
  extractPosix(stdout, "LC_CTYPE")?.let {
    MavenLog.LOG.debug("extracted charset $it from locale using LC_CTYPE")
    return it
  }
  MavenLog.LOG.debug("locale returned nothing, cannot determine charset")
  return null
}

private fun extractPosix(stdout: List<String>, name: String): String? {
  val prefix = "$name="
  return stdout
    .singleOrNull { it.startsWith(prefix) }
    ?.substring(prefix.length)
    ?.trim('"')
    .normalizeCharset()

}

private fun String?.normalizeCharset(): String? {
  val result = nullize(true)
    ?.substringAfter('.')
  if (result == "C") {
    MavenLog.LOG.debug("Default C locale, using 7-bit US-ASCII as charset. Will return null instead")
    return null
  }
  return result
}