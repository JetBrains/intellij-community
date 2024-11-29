package com.intellij.tools.devLauncher

import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Connects to IDE instance and start incremental compilation for [moduleNames] in it by sending a request which is processed by
 * [org.jetbrains.idea.devkit.requestHandlers.BuildHttpRequestHandler] provided by DevKit plugin.
 */
internal fun sendRequestToCompileModules(moduleNames: List<String>): Boolean {
  val projectHashProperty = "intellij.dev.project.location.hash"
  val projectHash = System.getProperty(projectHashProperty)
  if (projectHash == null) {
    System.err.println("'$projectHashProperty' is not specified. Make sure that you use IntelliJ IDEA 2024.2 or newer and have DevKit plugin enabled")
    return false
  }
  val serverPortProperty = "intellij.dev.ide.server.port"
  val idePort = System.getProperty(serverPortProperty)
  if (idePort == null) {
    System.err.println("'$serverPortProperty' is not specified. Make sure that you use IntelliJ IDEA 2024.2 or newer and have DevKit plugin enabled")
    return false
  }

  try {
    val host = "http://localhost:$idePort"
    println("Sending build request to $host:$idePort...")
    val startTime = System.currentTimeMillis()
    val url = URL("$host/devkit/build?project-hash=$projectHash")
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.useCaches = false
    connection.doOutput = true

    DataOutputStream(connection.outputStream).use { out ->
      out.writeBytes("""
       |[
       |{
       | "targetType": "java-production",
       | "targetIds": [${moduleNames.joinToString { "\"$it\"" }}]
       |} 
       |]
    """.trimMargin())
    }

    val responseCode = connection.responseCode
    if (responseCode != 200) {
      System.err.println("IDE responded with $responseCode:")
      System.err.println(connection.errorStream?.reader()?.readText())
      return false
    }

    println("Build finished successfully in ${System.currentTimeMillis() - startTime}ms")
    return true
  }
  catch (t: Throwable) {
    System.err.println("Failed to send request to build: $t")
    t.printStackTrace()
    return false
  }
}
