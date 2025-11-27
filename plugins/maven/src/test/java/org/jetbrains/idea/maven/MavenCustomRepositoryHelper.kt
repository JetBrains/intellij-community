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
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.delay
import org.jetbrains.idea.maven.utils.MavenLog
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

class MavenCustomRepositoryHelper(tempDir: Path, vararg subFolders: String) {
  private val myWorkingData: Path = tempDir.resolve("testData")

  init {
    Files.createDirectories(myWorkingData)
    for (each in subFolders) {
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
    val from = Paths.get(originalTestDataPath, relativePathFrom)
    Files.createDirectories(to.parent)
    Files.walkFileTree(from, object : SimpleFileVisitor<Path>() {
      @Throws(IOException::class)
      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        val target = to.resolve(from.relativize(file))
        Files.createDirectories(target.parent)
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
        return FileVisitResult.CONTINUE
      }
    })
    LocalFileSystem.getInstance().refreshNioFiles(mutableSetOf<Path?>(to))
    LocalFileSystem.getInstance().refreshNioFiles(mutableSetOf<Path?>(to))
  }

  fun getTestData(relativePath: String): Path {
    return myWorkingData.resolve(relativePath)
  }

  fun delete(relativePath: String) {
    try {
      val path = getTestData(relativePath)
      MavenLog.LOG.warn("Deleting $path")
      if (Files.isDirectory(path)) {
        // delete directory content recursively
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
          @Throws(IOException::class)
          override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.delete(file)
            return FileVisitResult.CONTINUE
          }

          @Throws(IOException::class)
          override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            Files.delete(dir)
            return FileVisitResult.CONTINUE
          }
        })
      }
      else {
        Files.deleteIfExists(path)
      }
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  @Throws(IOException::class)
  fun copy(fromRelativePath: String, toRelativePath: String) {
    val from = getTestData(fromRelativePath)
    val to = getTestData(toRelativePath)

    if (Files.isDirectory(from)) {
      Files.walkFileTree(from, object : SimpleFileVisitor<Path>() {
        @Throws(IOException::class)
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
          val target = to.resolve(from.relativize(file))
          Files.createDirectories(target.parent)
          Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
          return FileVisitResult.CONTINUE
        }
      })
    }
    else {
      Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
    }

    LocalFileSystem.getInstance().refreshNioFiles(mutableSetOf<Path?>(to))
  }

  // replace a file in repo; on Windows the file might be used by Maven process, hence multiple attempts
  suspend fun replaceFile(fromRelativePath: String, toRelativePath: String, nAttempts: Int = 5) {
    repeat(nAttempts) { attempt ->
      try {
        copy(fromRelativePath, toRelativePath)
        return
      }
      catch (e: Throwable) {
        if (attempt < nAttempts - 1) {
          delay(1000L)
        }
        else {
          throw e
        }
      }
    }
  }

  companion object {
    val originalTestDataPath: String
      get() {
        val sourcesDir = System.getProperty("maven.sources.dir", PluginPathManager.getPluginHomePath("maven"))
        return FileUtilRt.toSystemIndependentName("$sourcesDir/src/test/data")
      }
  }
}
