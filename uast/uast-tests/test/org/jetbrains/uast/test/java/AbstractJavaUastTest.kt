// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.uast.test.env.AbstractUastFixtureTest
import java.io.File

abstract class AbstractJavaUastTest : AbstractUastFixtureTest() {
  protected companion object {
    val TEST_JAVA_MODEL_DIR = File(PathManager.getCommunityHomePath(), "uast/uast-tests/java")
  }

  override fun getVirtualFile(testName: String): VirtualFile {
    val localPath = File(TEST_JAVA_MODEL_DIR, testName).path
    val vFile = LocalFileSystem.getInstance().findFileByPath(localPath)
    return vFile ?: throw IllegalStateException("Couldn't find virtual file for $localPath")
  }
}