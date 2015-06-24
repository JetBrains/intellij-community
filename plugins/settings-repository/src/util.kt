package org.jetbrains.settingsRepository

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import java.io.File
import java.nio.ByteBuffer
import javax.swing.SwingUtilities

public fun String?.nullize(): String? = StringUtil.nullize(this)

public fun byteBufferToBytes(byteBuffer: ByteBuffer): ByteArray {
  if (byteBuffer.hasArray() && byteBuffer.arrayOffset() == 0) {
    val bytes = byteBuffer.array()
    if (bytes.size() == byteBuffer.limit()) {
      return bytes
    }
  }

  val bytes = ByteArray(byteBuffer.limit())
  byteBuffer.get(bytes)
  return bytes
}

private fun getPathToBundledFile(filename: String): String {
  var pluginsDirectory: String
  var folder = "/settings-repository"
  if ("jar" == javaClass<IcsManager>().getResource("")!!.getProtocol()) {
    // running from build
    pluginsDirectory = PathManager.getPluginsPath()
    if (!File("$pluginsDirectory$folder").exists()) {
      pluginsDirectory = PathManager.getHomePath()
      folder = "/plugins$folder"
    }
  }
  else {
    // running from sources
    pluginsDirectory = PathManager.getHomePath()
  }
  return FileUtilRt.toSystemDependentName("$pluginsDirectory$folder/lib/$filename")
}

fun getPluginSystemDir(): File {
  val customPath = System.getProperty("ics.settingsRepository")
  if (customPath == null) {
    return File(PathManager.getConfigPath(), "settingsRepository")
  }
  else {
    return File(FileUtil.expandUserHome(customPath))
  }
}

fun invokeAndWaitIfNeed(runnable: ()->Unit) {
  if (SwingUtilities.isEventDispatchThread()) runnable() else SwingUtilities.invokeAndWait(runnable)
}