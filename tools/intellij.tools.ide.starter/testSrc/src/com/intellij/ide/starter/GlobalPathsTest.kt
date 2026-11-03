// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter

import com.intellij.ide.starter.path.GlobalPaths
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class GlobalPathsTest {
  @TempDir
  lateinit var checkoutDir: Path

  @Test
  fun `the test home stops the EditorConfig lookup of the checkout`() {
    val paths = object : GlobalPaths(checkoutDir) {}

    paths.testHomePath.resolve(".editorconfig").readText() shouldBe "root = true\n"
  }

  @Test
  fun `an existing editorconfig in the test home stays as it is`() {
    val editorConfig = checkoutDir.resolve("out/ide-tests").createDirectories().resolve(".editorconfig")
    editorConfig.writeText("root = true\n[*]\nindent_size = 4\n")

    object : GlobalPaths(checkoutDir) {}

    editorConfig.readText() shouldBe "root = true\n[*]\nindent_size = 4\n"
  }
}
