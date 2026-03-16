package com.intellij.ide.starter.community.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import java.time.LocalDate

data class ReleaseInfo(
  val date: LocalDate,
  /** @see [BuildType] */
  val type: String,
  val version: String,
  val majorVersion: String,
  // Some ancient releases of portable tools (Eg: dotTrace) may have this field = null
  @JsonSetter(nulls = Nulls.AS_EMPTY)
  val build: String,
  val downloads: Download,
)

data class Download(
  @JsonAlias("linux_x64")
  val linux: OperatingSystem?,

  @JsonAlias("linux_aarch64")
  @JsonProperty("linuxARM64")
  val linuxArm: OperatingSystem?,

  @JsonAlias("macos_x64", "mac")
  val mac: OperatingSystem?,

  @JsonAlias("macos_aarch64", "macARM64")
  val macM1: OperatingSystem?,

  @JsonAlias("windows_x64", "windows64")
  val windows: OperatingSystem?,

  @JsonAlias("windows_zip_x64")
  val windowsZip: OperatingSystem?,

  // TODO: Probably installation will not be supported in Starter framework (because Starter relies on archive for windows)
  @JsonAlias("windows_aarch64")
  @JsonProperty("windowsARM64")
  val windowsArm: OperatingSystem?,
)

data class OperatingSystem(
  val link: String,
  val size: Long,
  /**
   * Content of the response is something like this:
   * 7bc070eb7fc9a3496abc513f58291cf00dbb7fe27779df5bfb31abcf0ea26179 *Fleet-1.48.261.exe
   */
  val checksumLink: String,
)
