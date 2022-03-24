package com.intellij.ide.starter.process

data class MacOsProcessMetaInfo(
  override val pid: Int,
  override val command: String
) : ProcessMetaInfo(pid, command)