package org.jetbrains.settingsRepository

import com.intellij.openapi.util.io.FileUtil
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.junit.rules.TestName
import org.junit.runner.Description

import java.io.File
import java.io.IOException

public class GitTestWatcher : TestName() {
  var repository: Repository? = null

  throws(javaClass<IOException>())
  public fun getRepository(baseDir: File): Repository {
    if (repository == null) {
      repository = FileRepositoryBuilder().setWorkTree(File(baseDir, "upstream")).build()
      repository!!.create()
    }
    return repository!!
  }

  override fun finished(description: Description?) {
    super.finished(description)

    if (repository != null) {
      FileUtil.delete(repository!!.getWorkTree())
      repository = null
    }
  }
}