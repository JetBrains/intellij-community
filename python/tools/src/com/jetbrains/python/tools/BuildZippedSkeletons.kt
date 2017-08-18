package com.jetbrains.python.tools

import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.io.ZipUtil
import com.jetbrains.python.sdk.PythonSdkType
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

val PYCHARM_PYTHONS = "PYCHARM_PYTHONS"

fun main(args: Array<String>) {
  val app = IdeaTestApplication.getInstance()
  try {

    val root = System.getenv(PYCHARM_PYTHONS)

    for (python in File(root).listFiles()) {

      println("Running on $python")

      val sdk = PySdkTools.createTempSdk(VfsUtil.findFileByIoFile(File(PythonSdkType.getPythonExecutable(python.absolutePath)!!), true)!!,
                                         SdkCreationType.SDK_PACKAGES_AND_SKELETONS, null)

      val skeletonsDir = File(System.getProperty("user.dir"), "skeletons-${sdk.versionString!!.replace(" ", "_")}_" + +Math.abs(sdk.homePath!!.hashCode()))

      println("Generating skeletons in ${skeletonsDir.absolutePath}")

      val refresher = PySkeletonRefresher(null, null, sdk, skeletonsDir.absolutePath, null, null)


      refresher.regenerateSkeletons(SkeletonVersionChecker(0))


      val dirPacked = File(skeletonsDir.parent, refresher.pregeneratedSkeletonsName)
      val zip = ZipOutputStream(FileOutputStream(dirPacked))
      ZipUtil.addDirToZipRecursively(zip, dirPacked, skeletonsDir, "", null, null)
      zip.close()
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