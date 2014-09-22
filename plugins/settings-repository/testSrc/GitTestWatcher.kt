package org.jetbrains.settingsRepository

import com.intellij.openapi.util.io.FileUtil
import org.eclipse.jgit.lib.Repository
import org.junit.rules.TestName
import org.junit.runner.Description

import java.io.File
import java.io.IOException
import org.jetbrains.settingsRepository.git.createRepository

class GitTestWatcher : TestName() {
  var repository: Repository? = null

  throws(javaClass<IOException>())
  public fun getRepository(baseDir: File): Repository {
    if (repository == null) {
      repository = createRepository(File(baseDir, "upstream"))
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