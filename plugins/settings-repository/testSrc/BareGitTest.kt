// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.test

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.SmartList
import gnu.trove.THashMap
import gnu.trove.THashSet
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.settingsRepository.git.cloneBare
import org.jetbrains.settingsRepository.git.commit
import org.jetbrains.settingsRepository.git.processChildren
import org.jetbrains.settingsRepository.git.read
import org.junit.Rule
import org.junit.Test

internal class BareGitTest {
  @Rule
  @JvmField
  val tempDirManager = TemporaryDirectory()

  @Test fun `remote doesn't have commits`() {
    val repository = cloneBare(tempDirManager.createRepository("remote").workTree.absolutePath, tempDirManager.newPath("local"))
    assertThat(repository.read("\$ROOT_CONFIG$/keymaps/Mac OS X from RubyMine.xml")).isNull()
  }

  @Test fun bare() {
    val remoteRepository = tempDirManager.createRepository()
    val filePath = "keymaps/Mac OS X from RubyMine.xml"
    remoteRepository.add(filePath, SAMPLE_FILE_CONTENT)
    remoteRepository.add("keymapsZ.xml", "test")
    remoteRepository.commit("")

    val repository = cloneBare(remoteRepository.workTree.absolutePath, tempDirManager.newPath())
    assertThat(FileUtil.loadTextAndClose(repository.read(filePath)!!)).isEqualTo(SAMPLE_FILE_CONTENT)

    val list = SmartList<String>()
    repository.processChildren("keymaps") { name, _ ->
      list.add(name)
    }
    assertThat(list).containsOnly("Mac OS X from RubyMine.xml")
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

  @Test
  fun `processChildren not-master branch`() {
    val clonePath = tempDirManager.newPath()
    val repository = cloneBare("https://github.com/pronskiy/PhpStorm-Live-Templates-Craft-CMS.git", clonePath)

    val filePath = "templates"
    val data = THashSet<String>()
    repository.processChildren(filePath) { name, _ ->
      data.add(name)
      true
    }

    assertThat(data).isNotEmpty
  }
}