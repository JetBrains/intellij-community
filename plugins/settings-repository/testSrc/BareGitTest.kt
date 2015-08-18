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

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.TemporaryDirectory
import gnu.trove.THashMap
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.jetbrains.jgit.dirCache.AddFile
import org.jetbrains.jgit.dirCache.edit
import org.jetbrains.settingsRepository.git.cloneBare
import org.jetbrains.settingsRepository.git.commit
import org.jetbrains.settingsRepository.git.processChildren
import org.jetbrains.settingsRepository.git.read
import org.junit.Assert.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

class BareGitTest {
  val tempDirManager = TemporaryDirectory()
  public Rule fun getTemporaryFolder(): TemporaryDirectory = tempDirManager

  public Test fun `remote doesn't have commits`() {
    val repository = cloneBare(tempDirManager.createRepository("remote").getWorkTree().getAbsolutePath(), tempDirManager.newDirectory("local"))
    assertThat(repository.read("\$ROOT_CONFIG$/keymaps/Mac OS X from RubyMine.xml"), nullValue())
  }

  public Test fun bare() {
    val remoteRepository = tempDirManager.createRepository()
    val workTree: File = remoteRepository.getWorkTree()
    val filePath = "\$ROOT_CONFIG$/keymaps/Mac OS X from RubyMine.xml"
    val file = File(testDataPath, "remote.xml")
    FileUtil.copy(file, File(workTree, filePath))
    remoteRepository.edit(AddFile(filePath))
    remoteRepository.commit("")

    val repository = cloneBare(remoteRepository.getWorkTree().getAbsolutePath(), tempDirManager.newDirectory())
    assertThat(FileUtil.loadTextAndClose(repository.read(filePath)!!), equalTo(FileUtil.loadFile(file)))
  }

  public Test fun processChildren() {
    val remoteRepository = tempDirManager.createRepository()

    val workTree: File = remoteRepository.getWorkTree()
    val filePath = "\$ROOT_CONFIG$/keymaps/Mac OS X from RubyMine.xml"
    val file = File(testDataPath, "remote.xml")
    FileUtil.copy(file, File(workTree, filePath))
    remoteRepository.edit(AddFile(filePath))
    remoteRepository.commit("")

    val repository = cloneBare(remoteRepository.getWorkTree().getAbsolutePath(), tempDirManager.newDirectory())

    val data = THashMap<String, String>()
    repository.processChildren("\$ROOT_CONFIG$/keymaps") {name, input ->
      data.put(name, FileUtil.loadTextAndClose(input))
      true
    }

    assertThat(data.size(), equalTo(1))
    assertThat(data.get("Mac OS X from RubyMine.xml"), equalTo(FileUtil.loadFile(file)))
  }
}