// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter

import com.intellij.ide.starter.project.resetReusableCheckoutOnFirstUse
import com.intellij.ide.starter.utils.Git
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class GitReusableCheckoutResetTest {

  @Test
  fun `the first use resets the tracked files and removes the untracked and ignored files`(@TempDir checkout: Path) {
    commitProject(checkout)
    leaveTestEdits(checkout)

    resetReusableCheckoutOnFirstUse(checkout)

    checkout.resolve(TRACKED_FILE).readText() shouldBe ORIGINAL_TEXT
    checkout.resolve(UNTRACKED_FILE).shouldNotExist()
    checkout.resolve(IGNORED_FILE).shouldNotExist()
  }

  @Test
  fun `a later use in the same process keeps the checkout as it is`(@TempDir checkout: Path) {
    commitProject(checkout)
    resetReusableCheckoutOnFirstUse(checkout)
    leaveTestEdits(checkout)

    resetReusableCheckoutOnFirstUse(checkout)

    checkout.resolve(TRACKED_FILE).readText() shouldBe EDITED_TEXT
    checkout.resolve(UNTRACKED_FILE).shouldExist()
    checkout.resolve(IGNORED_FILE).shouldExist()
  }

  private fun commitProject(checkout: Path) {
    Git.init(checkout)
    Git.setConfigProperty(checkout, "user.name", "Starter Test")
    Git.setConfigProperty(checkout, "user.email", "starter-test@example.com")
    checkout.resolve(TRACKED_FILE).writeText(ORIGINAL_TEXT)
    checkout.resolve(".gitignore").writeText("build/\n")
    Git.addAll(checkout)
    Git.commit(checkout, "The test project")
  }

  private fun leaveTestEdits(checkout: Path) {
    checkout.resolve(TRACKED_FILE).writeText(EDITED_TEXT)
    checkout.resolve(UNTRACKED_FILE).writeText("A file that the test created")
    checkout.resolve(IGNORED_FILE).also { it.parent.createDirectories() }.writeText("The build output")
  }
}

private const val TRACKED_FILE = "Main.java"
private const val UNTRACKED_FILE = "Created.java"
private const val IGNORED_FILE = "build/Main.class"
private const val ORIGINAL_TEXT = "class Main {}"
private const val EDITED_TEXT = "class Main { int edited; }"
