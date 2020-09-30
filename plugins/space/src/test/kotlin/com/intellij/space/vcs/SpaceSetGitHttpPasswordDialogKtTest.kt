package com.intellij.space.vcs

import org.junit.Test
import kotlin.test.assertEquals

internal class SpaceSetGitHttpPasswordDialogKtTest {

  @Test
  fun getGitUrlHost() {
    assertEquals("http://git.jetbrains.team", getGitUrlHost("https://git.jetbrains.team/repo.git"))
    assertEquals("http://git.jetbrains.space", getGitUrlHost("https://git.jetbrains.space/org/repo.git"))
  }
}
