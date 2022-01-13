// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec

import com.intellij.debugger.streams.test.TraceExecutionTestCase
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import com.intellij.util.PathUtil
import java.io.File

/**
 * @author Vitaliy.Bibaev
 */
abstract class LibraryTraceExecutionTestCase(private val coordinates: String) : TraceExecutionTestCase() {
  lateinit var jarPath: String

  private companion object {
    fun String.replaceLibraryPath(libraryPath: String): String {
      val caseSensitive = SystemInfo.isFileSystemCaseSensitive
      val result = StringUtil.replace(this, FileUtil.toSystemDependentName(libraryPath), "!LIBRARY_JAR!", !caseSensitive)
      return StringUtil.replace(result, FileUtil.toSystemIndependentName(libraryPath), "!LIBRARY_JAR!", !caseSensitive)
    }
  }

  override fun setUpModule() {
    super.setUpModule()
    ModuleRootModificationUtil.updateModel(myModule) { model ->
      MavenDependencyUtil.addFromMaven(model, coordinates)
      val libraryJar = model.moduleLibraryTable.getLibraryByName(coordinates)!!.rootProvider.getFiles(OrderRootType.CLASSES)[0]
      jarPath = PathUtil.getLocalPath(libraryJar)!!
    }
  }

  override fun replaceAdditionalInOutput(str: String): String {
    return super.replaceAdditionalInOutput(str).replaceLibraryPath(jarPath)
  }

  override fun createJavaParameters(mainClass: String?): JavaParameters {
    val parameters = super.createJavaParameters(mainClass)
    parameters.classPath.add(jarPath)
    return parameters
  }

  final override fun getTestAppPath(): String {
    return File(PluginPathManager.getPluginHomePath("stream-debugger") + "/testData/${getTestAppRelativePath()}").absolutePath
  }

  abstract fun getTestAppRelativePath(): String
}