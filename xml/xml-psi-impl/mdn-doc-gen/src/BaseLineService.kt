import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.lang.javascript.JSStringUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException

object BaseLineService {
  private val logger = com.intellij.openapi.diagnostic.Logger.getInstance(BaseLineService::class.java)
  private var process: Process? = null
  private var writer: BufferedWriter? = null
  private var reader: BufferedReader? = null
  private val gson = GsonBuilder().create()

  @Synchronized
  fun start() {
    if (process != null) {
      return
    }

    try {
      val baselineServerPath = FileUtil.toSystemDependentName(
        "${PathManager.getCommunityHomePath()}/xml/xml-psi-impl/mdn-doc-gen/baseline-server"
      )

      val processBuilder = ProcessBuilder("node", "index.mjs")
      processBuilder.directory(File(baselineServerPath))
      processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)

      process = processBuilder.start()
      writer = process!!.outputStream.bufferedWriter()
      reader = process!!.inputStream.bufferedReader()

      // Add shutdown hook to ensure process is terminated when JVM exits
      Runtime.getRuntime().addShutdownHook(Thread {
        stop()
      })
    }
    catch (e: Exception) {
      logger.error("Failed to start BaseLineService", e)
      stop()
      throw e
    }
  }

  @Synchronized
  fun stop() {
    try {
      try {
        writer?.close()
      }
      catch (_: IOException) {
      }
      try {
        reader?.close()
      }
      catch (_: IOException) {
      }
      process?.let {
        if (it.isAlive) {
          it.destroy()
          it.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
          if (it.isAlive) {
            it.destroyForcibly()
          }
        }
      }
    }
    catch (e: Exception) {
      logger.error("Error stopping BaseLineService", e)
    }
    finally {
      writer = null
      reader = null
      process = null
      logger.info("BaseLineService stopped")
    }
  }

  @Synchronized
  fun computeBaseline(compatKey: String): JsonObject? {
    if (process == null || writer == null || reader == null) {
      start()
    }

    try {
      // Write the compatKey to stdin
      writer!!.write(compatKey)
      writer!!.newLine()
      writer!!.flush()

      // Read the response from stdout
      val response = reader!!.readLine() ?: return null

      // Parse the JSON response
      val json = JSStringUtil.unquoteAndUnescapeString(response)
      if (json == "null")
        return null
      return gson.fromJson(json, JsonObject::class.java)
    }
    catch (e: Exception) {
      logger.error("Error computing baseline for key: $compatKey", e)
      if (e is IOException) {
        stop()
        start()
      }
      return null
    }
  }
}
