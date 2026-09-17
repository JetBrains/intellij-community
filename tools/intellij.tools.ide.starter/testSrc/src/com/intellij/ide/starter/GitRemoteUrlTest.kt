// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter

import com.intellij.ide.starter.utils.Git
import com.intellij.ide.starter.utils.setRemoteUrlIfDifferent
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

private const val HTTPS_URL = "https://github.com/jitpack/android-example.git"
private const val SSH_URL = "ssh://git@github.com/jitpack/android-example.git"

class GitRemoteUrlTest {

  @Test
  fun `a cached checkout moves to the new URL`(@TempDir repository: Path) {
    Git.init(repository)
    Git.addRemote(repository, "origin", HTTPS_URL)

    Git.setRemoteUrlIfDifferent(repository, SSH_URL) shouldBe true
    Git.getRemoteUrl(repository, "origin") shouldBe SSH_URL
  }

  @Test
  fun `the same URL is left alone`(@TempDir repository: Path) {
    Git.init(repository)
    Git.addRemote(repository, "origin", SSH_URL)

    Git.setRemoteUrlIfDifferent(repository, SSH_URL) shouldBe false
    Git.getRemoteUrl(repository, "origin") shouldBe SSH_URL
  }

  /** `git remote set-url` fails on a remote that does not exist, so a repository with no origin is left alone. */
  @Test
  fun `a repository with no origin is left alone`(@TempDir repository: Path) {
    Git.init(repository)

    Git.setRemoteUrlIfDifferent(repository, SSH_URL) shouldBe false
    Git.getRemoteUrl(repository, "origin") shouldBe ""
  }
}