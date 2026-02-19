// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.test

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.SmartList
import com.intellij.util.containers.CollectionFactory
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.lib.Constants
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
    cloneBare(tempDirManager.createRepository("remote").workTree.absolutePath, tempDirManager.newPath("local")).use { repository ->
      assertThat(repository.read("\$ROOT_CONFIG$/keymaps/Mac OS X from RubyMine.xml")).isNull()
    }
  }

  @Test fun bare() {
    tempDirManager.createRepository().use { remoteRepository ->
      val filePath = "keymaps/Mac OS X from RubyMine.xml"
      remoteRepository.add(filePath, SAMPLE_FILE_CONTENT)
      remoteRepository.add("keymapsZ.xml", "test")
      remoteRepository.commit("")

      cloneBare(remoteRepository.workTree.absolutePath, tempDirManager.newPath()).use { repository ->
        assertThat(FileUtil.loadTextAndClose(repository.read(filePath)!!)).isEqualTo(SAMPLE_FILE_CONTENT)

        val list = SmartList<String>()
        repository.processChildren("keymaps") { name, _ ->
          list.add(name)
        }
        assertThat(list).containsOnly("Mac OS X from RubyMine.xml")
      }
    }
  }

  @Test fun defaultBranch() {
    tempDirManager.createRepository().use { remoteRepository ->
      val filePath = "keymaps/Mac OS X from RubyMine.xml"
      remoteRepository.add(filePath, SAMPLE_FILE_CONTENT)
      remoteRepository.add("keymapsZ.xml", "test")
      remoteRepository.commit("")

      remoteRepository.updateRef("refs/merge-requests/1/head").apply {
        val headId = remoteRepository.findRef(Constants.HEAD).objectId
        setNewObjectId(headId)
        forceUpdate()
      }

      cloneBare(remoteRepository.workTree.absolutePath, tempDirManager.newPath()).use { repository ->
        assertThat(FileUtil.loadTextAndClose(repository.read(filePath)!!)).isEqualTo(SAMPLE_FILE_CONTENT)
      }
    }
  }

  @Test fun processChildren() {
    tempDirManager.createRepository().use { remoteRepository ->

      val filePath = "keymaps/Mac OS X from RubyMine.xml"
      remoteRepository.add(filePath, SAMPLE_FILE_CONTENT)
      remoteRepository.commit("")

      cloneBare(remoteRepository.workTree.absolutePath, tempDirManager.newPath()).use { repository ->

        val data = HashMap<String, String>()
        repository.processChildren("keymaps") { name, input ->
          data.put(name, FileUtil.loadTextAndClose(input))
          true
        }

        assertThat(data).hasSize(1)
        assertThat(data.get("Mac OS X from RubyMine.xml")).isEqualTo(SAMPLE_FILE_CONTENT)
      }
    }
  }

  @Test
  fun `processChildren not-master branch`() {
    val clonePath = tempDirManager.newPath()
    cloneBare("https://github.com/pronskiy/PhpStorm-Live-Templates-Craft-CMS.git", clonePath).use { repository ->

      val filePath = "templates"
      val data = CollectionFactory.createSmallMemoryFootprintSet<String>()
      repository.processChildren(filePath) { name, _ ->
        data.add(name)
        true
      }

      assertThat(data).isNotEmpty
    }
  }
}