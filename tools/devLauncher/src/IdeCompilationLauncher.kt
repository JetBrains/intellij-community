package com.intellij.tools.devLauncher

import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Connects to IDE instance and start incremental compilation for [moduleNames] in it by sending a request which is processed by
 * [org.jetbrains.idea.devkit.requestHandlers.BuildHttpRequestHandler] provided by DevKit plugin.
 */
internal fun sendRequestToCompileModules(moduleNames: List<String>): Boolean {
  val homeProperty = "idea.home.path"
  val projectHome = System.getProperty(homeProperty)
  if (projectHome == null) {
    System.err.println("'$homeProperty' is not specified")
    return false
  }
  
  try {
    val defaultIdePort = 63342
    for (idePort in defaultIdePort..defaultIdePort + 10) {
      val host = "http://localhost:$idePort"
      println("Sending build request to $host...")
      val startTime = System.currentTimeMillis()
      val url = URL("$host/devkit/build?project-path=$projectHome")
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
      if (responseCode == 404) {
        println("IDE responded with $responseCode, trying on the next port...")
        continue
      }

      if (responseCode != 200) {
        System.err.println("IDE responded with $responseCode:")
        System.err.println(connection.errorStream?.reader()?.readText())
        return false
      }
      
      println("Build finished successfully in ${System.currentTimeMillis() - startTime}ms")
      return true
    } 
  }
  catch (t: Throwable) {
    System.err.println("Failed to send request to build: $t")
    t.printStackTrace()
    return false
  }

  System.err.println("Failed to connect to an IDE")
  return false
}
