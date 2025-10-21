/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven

import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class MavenCustomNioRepositoryHelper(myTempDir: Path, vararg subFolders: String) {
  private val myWorkingData: Path = myTempDir.resolve("testData")
  private val mySubFolders = subFolders

  init {
    if (!Files.exists(myWorkingData)) {
      Files.createDirectories(myWorkingData)
    }

    for (each in mySubFolders) {
      addTestData(each)
    }
  }

  @Throws(IOException::class)
  fun addTestData(relativePath: String) {
    addTestData(relativePath, relativePath)
  }

  @Throws(IOException::class)
  fun addTestData(relativePathFrom: String, relativePathTo: String) {
    val to = myWorkingData.resolve(relativePathTo)
    val from: Path = originalTestDataPath.resolve(relativePathFrom)
    Files.copy(from, to)
    LocalFileSystem.getInstance().refreshNioFiles(mutableSetOf<Path?>(to))
  }

  fun getTestData(relativePath: String): Path {
    return myWorkingData.resolve(relativePath)
  }

  fun delete(relativePath: String) {
    try {
      NioFiles.deleteRecursively(getTestData(relativePath))
    }
    catch (_: IOException) {
      throw IllegalStateException("Unable to delete $relativePath")
    }
  }

  @Throws(IOException::class)
  fun copy(fromRelativePath: String, toRelativePath: String) {
    val from = getTestData(fromRelativePath)
    val to = getTestData(toRelativePath)

    if (Files.isDirectory(from)) {
      NioFiles.copyRecursively(from, to)
    }
    else {
      Files.copy(from, to)
    }

    LocalFileSystem.getInstance().refreshNioFiles(mutableSetOf<Path?>(to))
  }

  companion object {
    val originalTestDataPath: Path
      get() {
        val sourcesDir = System.getProperty("maven.sources.dir", PluginPathManager.getPluginHomePath("maven"))
        val originalTestDataPath = sourcesDir.toNioPathOrNull()
        return originalTestDataPath!!.resolve("src/test/data")
      }
  }
}
