@file:Suppress("IO_FILE_USAGE")

package com.intellij.mcpserver

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.mcpserver.stdio.*
import com.intellij.openapi.application.PathManager
import java.io.File
import kotlin.io.path.pathString
import kotlin.reflect.jvm.javaMethod

/**
 * Build a commandline to run MCP IDE server in a separate process
 */
fun createStdioMcpServerCommandLine(ideServerPort: Int, serverName: String?, serverVersion: String?, projectBasePath: String?): GeneralCommandLine {
  val classpaths = McpStdioRunnerClasspath.CLASSPATH_CLASSES.map {
    (PathManager.getJarForClass(it) ?: error("No path for class $it")).pathString
  }.toSet()

  val commandLine = GeneralCommandLine()
    .withExePath("${System.getProperty("java.home")}${File.separator}bin${File.separator}java")
    .withParameters("-classpath", classpaths.joinToString(File.pathSeparator))
    .withParameters(::main.javaMethod!!.declaringClass.name)
    .withEnvironment(IJ_MCP_SERVER_PORT, ideServerPort.toString())
  if (serverName != null) commandLine.withEnvironment(IJ_MCP_SERVER_NAME, serverName)
  if (serverVersion != null) commandLine.withEnvironment(IJ_MCP_SERVER_VERSION, serverVersion)
  if (projectBasePath != null) commandLine.withEnvironment(IJ_MCP_SERVER_PROJECT_PATH, projectBasePath)
  return commandLine
}