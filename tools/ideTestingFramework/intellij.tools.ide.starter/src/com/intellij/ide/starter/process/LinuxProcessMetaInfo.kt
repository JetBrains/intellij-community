// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.process

data class LinuxProcessMetaInfo(
  override val pid: Int,
  val vsz: Int,
  val rss: Int,
  override val command: String
) : ProcessMetaInfo(pid, command) {
  override fun toString() = "$pid (vsz = $vsz) (rss = $rss) $command"
}