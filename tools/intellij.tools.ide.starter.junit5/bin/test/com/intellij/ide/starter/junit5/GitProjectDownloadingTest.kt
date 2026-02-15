package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.junit5.config.KillOutdatedProcessesAfterEach
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.project.GitProjectInfo
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.kodein.di.instance
import java.util.concurrent.TimeUnit

@ExtendWith(KillOutdatedProcessesAfterEach::class)
class GitProjectDownloadingTest {

  @Test
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  fun downloadGitRepoProject() {
    val gitProject = GitProjectInfo(
      branchName = "gh-pages",
      repositoryUrl = "https://github.com/facebookresearch/encodec"
    )
      .onCommit("59d463f77b872474e4beb50d896db9eb326841da")

    val projectPath = gitProject.downloadAndUnpackProject()

    projectPath.shouldExist()

    val globalPaths by di.instance<GlobalPaths>()
    projectPath.parent.shouldBe(globalPaths.cacheDirForProjects.resolve("unpacked"))
  }
}