/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.tools

import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.io.ZipUtil
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.skeletons.DefaultPregeneratedSkeletonsProvider
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher
import com.jetbrains.python.sdk.skeletons.SkeletonVersionChecker
import com.jetbrains.python.tools.sdkTools.PySdkTools
import com.jetbrains.python.tools.sdkTools.SdkCreationType
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipOutputStream

/**
 * @author traff
 */

const val PYCHARM_PYTHONS: String = "PYCHARM_PYTHONS"

fun main(args: Array<String>) {
  println("Starting build process")
  val app = IdeaTestApplication.getInstance()
  println("App started: ${app}")
  try {

    val root = System.getenv(PYCHARM_PYTHONS)

    for (python in File(root).listFiles()) {

      println("Running on $python")

      val executable = PythonSdkType.getPythonExecutable(python.absolutePath)!!
      val sdk = PySdkTools.createTempSdk(VfsUtil.findFileByIoFile(File(executable), true)!!,
                                         SdkCreationType.SDK_PACKAGES_AND_SKELETONS, null)

      val skeletonsDir = File(System.getProperty("user.dir"),
                              "skeletons-${sdk.versionString!!.replace(" ", "_")}_" + Math.abs(sdk.homePath!!.hashCode()))

      println("Generating skeletons in ${skeletonsDir.absolutePath}")

      val refresher = PySkeletonRefresher(null, null, sdk, skeletonsDir.absolutePath, null, null)


      refresher.regenerateSkeletons(SkeletonVersionChecker(SkeletonVersionChecker.PREGENERATED_VERSION))


      val dirPacked = File(skeletonsDir.parent, DefaultPregeneratedSkeletonsProvider.getPregeneratedSkeletonsName(sdk, refresher.generatorVersion, true, true))
      ZipOutputStream(FileOutputStream(dirPacked)).use {
        ZipUtil.addDirToZipRecursively(it, dirPacked, skeletonsDir, "", null, null)
      }
    }
  }
  catch (e: Exception) {
    e.printStackTrace()
    System.exit(1)
  }
  finally {
    System.exit(0) //TODO: graceful exit?
  }
}