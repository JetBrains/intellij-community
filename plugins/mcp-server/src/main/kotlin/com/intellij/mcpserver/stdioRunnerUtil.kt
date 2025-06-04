@file:Suppress("IO_FILE_USAGE")

package com.intellij.mcpserver

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.mcpserver.stdio.main
import com.intellij.openapi.application.PathManager
import java.io.File
import kotlin.io.path.pathString
import kotlin.reflect.jvm.javaMethod

/**
 * Build a commandline to run MCP IDE server in a separate process
 */
fun createStdioMcpServerCommandLine(): GeneralCommandLine {
  val classpaths = McpStdioRunnerClasspath.CLASSPATH_CLASSES.map {
    (PathManager.getJarForClass(it) ?: error("No path for class $it")).pathString
  }.toSet()

  return GeneralCommandLine()
    .withExePath("${System.getProperty("java.home")}${File.separator}bin${File.separator}java")
    .withParameters("-classpath", classpaths.joinToString(File.pathSeparator))
    .withParameters(::main.javaMethod!!.declaringClass.name)
}