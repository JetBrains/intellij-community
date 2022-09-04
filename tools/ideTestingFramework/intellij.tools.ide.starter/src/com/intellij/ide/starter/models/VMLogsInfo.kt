package com.intellij.ide.starter.models

import java.io.File

data class VMLogsInfo(val cdsLoadedClassesCount: Int,
                      val classLoadLogFile: File,
                      val classPathLogFile: File,
                      val startResult: IDEStartResult) {

  val classPathLog get() = classPathLogFile.readText()
}