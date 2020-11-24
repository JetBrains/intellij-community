// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.python.sdk.PySdkHome.Companion.fromBinaryPathOrDirectory
import java.io.File

/**
 * Certain python installation. It could be system python or virtual environment.
 * For virtual environment [getRootDir] and [findBinaryInSdk] are supported.
 * Create with [fromBinaryPathOrDirectory]
 */
class PySdkHome private constructor(val pythonBinary: File) {

  companion object {

    /**
     * Creates instance from path to binary file or virtualenv dir.
     */
    fun fromBinaryPathOrDirectory(binaryOrFolderPath: String) : PySdkHome? {
      val binaryOrFolder = File(binaryOrFolderPath)
      if (binaryOrFolder.isDirectory) {
        val file = "python" + if (SystemInfoRt.isWindows) ".exe" else ""
        for (possibleFile in listOf(File(binaryOrFolder, file), File(binaryOrFolder, "/bin/$file"))) {
          if (possibleFile.normalExecutable) return PySdkHome(possibleFile)
        }
      }
      return createByFile(binaryOrFolder)
    }

    private fun createByFile(pythonBinary: File): PySdkHome? = if (pythonBinary.normalExecutable) PySdkHome(pythonBinary) else null

    private val File.normalExecutable
      get() = canExecute()
  }

  val pythonBinaryPath: String get() = pythonBinary.absolutePath

  /**
   * Virtual envs has root dir
   */
  fun getRootDir(): File? {
    val dirWithPython = pythonBinary.parentFile
    if (dirWithPython.name == "bin" && !SystemInfoRt.isWindows) dirWithPython.parentFile
    if (SystemInfoRt.isWindows) return dirWithPython
    return null
  }


  /**
   * Looks for file named [requestedBinFileName] (along with extension for Windows) in [getRootDir].
   * Only works for Virtual environment
   */
  fun findBinaryInSdk(requestedBinFileName: String): File? {
    assert('/' !in requestedBinFileName && '\\' !in requestedBinFileName) { "Provide only file name" }
    val parent = getRootDir() ?:return null
    val file = File(parent, if (SystemInfoRt.isWindows) "Scripts/$requestedBinFileName" else requestedBinFileName)
    return if (file.normalExecutable) file else null
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PySdkHome

    if (!FileUtil.filesEqual(pythonBinary, other.pythonBinary)) return false

    return true
  }


  override fun hashCode(): Int {
    return FileUtil.fileHashCode(pythonBinary)
  }

  override fun toString(): String {
    return "PySdkHome(pythonBinary=$pythonBinary)"
  }


}