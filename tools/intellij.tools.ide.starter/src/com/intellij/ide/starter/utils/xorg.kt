package com.intellij.ide.starter.utils

import com.intellij.ide.starter.process.getProcessList
import com.intellij.tools.ide.util.common.logOutput

fun getRunningDisplays(): List<Int> {
  logOutput("Looking for running displays")
  val xvfbWithDisplayProcessList = getProcessList { it.name == "Xvfb" && it.arguments.singleOrNull { arg -> arg.startsWith(":") } != null }
  val displays = xvfbWithDisplayProcessList.mapNotNull { it.arguments.single { arg -> arg.startsWith(":") }.substring(1).toIntOrNull() }
  if (displays.isEmpty()) {
    val xvfbProcessList = getProcessList { it.name == "Xvfb" }
    logOutput("Have not found running xvfb displays\n." +
              "Full xvfb process list was: ${xvfbProcessList.joinToString("\n") { it.description }}")
  }
  else {
    logOutput("Found Xvfb displays: $displays.")
  }
  return displays
}