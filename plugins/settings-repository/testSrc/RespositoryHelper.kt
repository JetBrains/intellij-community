/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.settingsRepository.test

import com.intellij.mock.MockVirtualFileSystem
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import org.eclipse.jgit.lib.Constants
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import java.io.File
import java.util.Arrays
import java.util.Comparator

data class FileInfo(val name: String, val data: ByteArray)

fun fs(vararg paths: String): MockVirtualFileSystem {
  val fs = MockVirtualFileSystem()
  for (path in paths) {
    fs.findFileByPath(path)
  }
  return fs
}

private fun compareFiles(local: File, remote: File, expected: VirtualFile? = null, vararg localExcludes: String) {
  var localFiles = local.list()!!
  var remoteFiles = remote.list()!!

  localFiles = ArrayUtil.remove(localFiles, Constants.DOT_GIT)
  remoteFiles = ArrayUtil.remove(remoteFiles, Constants.DOT_GIT)

  Arrays.sort(localFiles)
  Arrays.sort(remoteFiles)

  if (localExcludes.size() != 0) {
    for (localExclude in localExcludes) {
      localFiles = ArrayUtil.remove(localFiles, localExclude)
    }
  }

  assertThat(localFiles, equalTo(remoteFiles))

  val expectedFiles: Array<VirtualFile>?
  if (expected == null) {
    expectedFiles = null
  }
  else {
    //noinspection UnsafeVfsRecursion
    expectedFiles = expected.getChildren()
    Arrays.sort(expectedFiles!!, object : Comparator<VirtualFile> {
      override fun compare(o1: VirtualFile, o2: VirtualFile): Int {
        return o1.getName().compareTo(o2.getName())
      }
    })

    for (i in 0..expectedFiles.size() - 1) {
      assertThat(localFiles[i], equalTo(expectedFiles[i].getName()))
    }
  }

  for (i in 0..localFiles.size() - 1) {
    val localFile = File(local, localFiles[i])
    val remoteFile = File(remote, remoteFiles[i])
    val expectedFile: VirtualFile?
    if (expectedFiles == null) {
      expectedFile = null
    }
    else {
      expectedFile = expectedFiles[i]
      assertThat(expectedFile.isDirectory(), equalTo(localFile.isDirectory()))
    }

    if (localFile.isFile()) {
      assertThat(FileUtil.loadFile(localFile), equalTo(FileUtil.loadFile(remoteFile)))
    }
    else {
      compareFiles(localFile, remoteFile, expectedFile, *localExcludes)
    }
  }
}