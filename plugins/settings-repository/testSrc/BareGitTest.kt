package org.jetbrains.settingsRepository.test

import com.intellij.openapi.util.io.FileUtil
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.jetbrains.jgit.dirCache.AddFile
import org.jetbrains.jgit.dirCache.edit
import org.jetbrains.settingsRepository.git.cloneBare
import org.jetbrains.settingsRepository.git.commit
import org.jetbrains.settingsRepository.git.read
import org.junit.Assert
import org.junit.Test
import java.io.File

class BareGitTest : TestCase() {
  public Test fun `remote doesn't have commits`() {
    val remoteRepository = getRemoteRepository()
    val dir = FileUtil.generateRandomTemporaryPath()
    val repository = cloneBare(remoteRepository.getWorkTree().getAbsolutePath(), dir)
    Assert.assertThat(repository.read("\$ROOT_CONFIG$/keymaps/Mac OS X from RubyMine.xml"), nullValue())
  }

  public Test fun bare() {
    val remoteRepository = getRemoteRepository()

    val workTree: File = remoteRepository.getWorkTree()
    val filePath = "\$ROOT_CONFIG$/keymaps/Mac OS X from RubyMine.xml"
    val file = File(testDataPath, "remote.xml")
    FileUtil.copy(file, File(workTree, filePath))
    remoteRepository.edit(AddFile(filePath))
    remoteRepository.commit("")

    val dir = FileUtil.generateRandomTemporaryPath()
    val repository = cloneBare(remoteRepository.getWorkTree().getAbsolutePath(), dir)
    Assert.assertThat(FileUtil.loadTextAndClose(repository.read(filePath)!!), equalTo(FileUtil.loadFile(file)))
  }
}