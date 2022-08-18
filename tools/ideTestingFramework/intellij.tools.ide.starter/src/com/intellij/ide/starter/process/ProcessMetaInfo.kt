package com.intellij.ide.starter.process

abstract class ProcessMetaInfo(
  open val pid: Int,
  open val command: String
) {
  override fun toString() = "$pid $command"
}
