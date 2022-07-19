// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.models

import java.io.File

data class VMLogsInfo(val cdsLoadedClassesCount: Int,
                      val classLoadLogFile: File,
                      val classPathLogFile: File,
                      val startResult: IDEStartResult) {

  val classPathLog get() = classPathLogFile.readText()
}