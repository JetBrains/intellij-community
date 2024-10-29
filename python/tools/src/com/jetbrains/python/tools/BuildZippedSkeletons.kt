// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.tools

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.util.io.Compressor
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.VirtualEnvReader
import com.jetbrains.python.sdk.skeletons.DefaultPregeneratedSkeletonsProvider
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher
import com.jetbrains.python.tools.sdkTools.PySdkTools
import com.jetbrains.python.tools.sdkTools.SdkCreationType
import java.io.File
import kotlin.io.path.Path
import kotlin.math.abs
import kotlin.system.exitProcess

internal const val PYCHARM_PYTHONS = "PYCHARM_PYTHONS"

fun main() {
  println("Starting build process")
  val app = TestApplicationManager.getInstance()
  println("App started: ${app}")

  try {
    val root = System.getenv(PYCHARM_PYTHONS) ?: error("$PYCHARM_PYTHONS environment variable is undefined")
    val workingDir = System.getProperty("user.dir")
    val cacheDir = File(workingDir, "cache")
    println("Skeletons will share a common cache at $cacheDir")

    for (python in File(root).listFiles()!!) {
      println("Running on $python")

      val executable =  VirtualEnvReader.Instance.findPythonInPythonRoot(Path(python.absolutePath))!!.toString()
      val sdk = PySdkTools.createTempSdk(VfsUtil.findFileByIoFile(File(executable), true)!!, SdkCreationType.SDK_PACKAGES_ONLY, null, null)

      val skeletonsDir = File(workingDir, "skeletons-${sdk.versionString!!.replace(" ", "_")}_" + abs(sdk.homePath!!.hashCode()))
      println("Generating skeletons in ${skeletonsDir.absolutePath}")

      val refresher = PySkeletonRefresher(null, null, sdk, skeletonsDir.absolutePath, null, null)
      refresher.generator
        .commandBuilder()
        .inPrebuildingMode()
        .runGeneration(null)

      val artifactName = DefaultPregeneratedSkeletonsProvider.getPregeneratedSkeletonsName(sdk, refresher.generatorVersion, true, true)
      val dirPacked = File(skeletonsDir.parent, artifactName!!)
      println("Creating artifact $dirPacked")
      Compressor.Zip(dirPacked).use { it.addDirectory(skeletonsDir) }
    }
  }
  catch (e: Exception) {
    e.printStackTrace()
    exitProcess(1)
  }
  finally {
    exitProcess(0) //TODO: a graceful exit?
  }
}