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
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.settingsRepository.git.cloneBare
import org.jetbrains.settingsRepository.git.commit
import org.jetbrains.settingsRepository.git.processChildren
import org.jetbrains.settingsRepository.git.read
import org.junit.Rule
import org.junit.Test

internal class BareGitTest {
  val tempDirManager = TemporaryDirectory()
  @Rule fun getTemporaryFolder() = tempDirManager

  @Test fun `remote doesn't have commits`() {
    val repository = cloneBare(tempDirManager.createRepository("remote").workTree.absolutePath, tempDirManager.newPath("local"))
    assertThat(repository.read("\$ROOT_CONFIG$/keymaps/Mac OS X from RubyMine.xml")).isNull()
  }

  @Test fun bare() {
    val remoteRepository = tempDirManager.createRepository()
    val filePath = "keymaps/Mac OS X from RubyMine.xml"
    remoteRepository.add(filePath, SAMPLE_FILE_CONTENT)
    remoteRepository.commit("")

    val repository = cloneBare(remoteRepository.workTree.absolutePath, tempDirManager.newPath())
    assertThat(FileUtil.loadTextAndClose(repository.read(filePath)!!)).isEqualTo(SAMPLE_FILE_CONTENT)
  }

  @Test fun processChildren() {
    val remoteRepository = tempDirManager.createRepository()

    val filePath = "keymaps/Mac OS X from RubyMine.xml"
    remoteRepository.add(filePath, SAMPLE_FILE_CONTENT)
    remoteRepository.commit("")

    val repository = cloneBare(remoteRepository.workTree.absolutePath, tempDirManager.newPath())

    val data = THashMap<String, String>()
    repository.processChildren("keymaps") {name, input ->
      data.put(name, FileUtil.loadTextAndClose(input))
      true
    }

    assertThat(data).hasSize(1)
    assertThat(data.get("Mac OS X from RubyMine.xml")).isEqualTo(SAMPLE_FILE_CONTENT)
  }
}