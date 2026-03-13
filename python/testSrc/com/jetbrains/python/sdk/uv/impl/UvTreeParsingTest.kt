// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.impl

import com.jetbrains.python.packaging.packageRequirements.PythonPackageRequirementsTreeExtractor.Companion.parseTree
import com.jetbrains.python.packaging.packageRequirements.TreeParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UvTreeParsingTest {

  @Nested
  inner class ParsePackageList {

    @Test
    fun `simple non-workspace project`() {
      val input = """
        monorepo v0.1.0
        ├── requests v2.31.0
        ├── flask v3.0.0
        └── numpy v1.26.0
      """.trimIndent()

      val packages = UvOutputParser.parseUvPackageList(input)

      assertThat(packages).hasSize(3)
      assertThat(packages.map { it.name }).containsExactly("requests", "flask", "numpy")
      assertThat(packages.map { it.version }).containsExactly("2.31.0", "3.0.0", "1.26.0")
    }

    @Test
    fun `workspace project stops at first workspace member section`() {
      val input = """
        monorepo v0.1.0
        ├── cli v0.1.0
        ├── requests v2.31.0
        cli v0.1.0
        ├── lib v0.1.0
        lib v0.1.0
      """.trimIndent()

      val packages = UvOutputParser.parseUvPackageList(input)

      assertThat(packages).hasSize(2)
      assertThat(packages.map { it.name }).containsExactly("cli", "requests")
    }

    @Test
    fun `empty tree with header only`() {
      val input = "monorepo v0.1.0"

      val packages = UvOutputParser.parseUvPackageList(input)

      assertThat(packages).isEmpty()
    }

    @Test
    fun `root with no deps followed by workspace member section`() {
      val input = """
        monorepo v0.1.0
        cli v0.1.0
        ├── lib v0.1.0
      """.trimIndent()

      val packages = UvOutputParser.parseUvPackageList(input)

      assertThat(packages).isEmpty()
    }

    @Test
    fun `blank lines between sections are skipped`() {
      val input = "monorepo v0.1.0\n" +
                  "├── requests v2.31.0\n" +
                  "\n" +
                  "cli v0.1.0\n" +
                  "├── lib v0.1.0\n"

      val packages = UvOutputParser.parseUvPackageList(input)

      assertThat(packages).hasSize(1)
      assertThat(packages[0].name).isEqualTo("requests")
      assertThat(packages[0].version).isEqualTo("2.31.0")
    }

    @Test
    fun `version v prefix is stripped`() {
      val input = """
        myapp v1.0.0
        ├── requests v2.31.0
        └── flask v3.0.0
      """.trimIndent()

      val packages = UvOutputParser.parseUvPackageList(input)

      assertThat(packages.map { it.version }).containsExactly("2.31.0", "3.0.0")
    }

    @Test
    fun `single dependency`() {
      val input = """
        myapp v1.0.0
        └── requests v2.31.0
      """.trimIndent()

      val packages = UvOutputParser.parseUvPackageList(input)

      assertThat(packages).hasSize(1)
      assertThat(packages[0].name).isEqualTo("requests")
      assertThat(packages[0].version).isEqualTo("2.31.0")
    }

    @Test
    fun `workspace with multiple member sections`() {
      val input = """
        monorepo v0.1.0
        ├── cli v0.1.0
        ├── requests v2.31.0
        cli v0.1.0
        ├── lib v0.1.0
        ├── click v8.1.0
        lib v0.1.0
        └── pydantic v2.0.0
      """.trimIndent()

      val packages = UvOutputParser.parseUvPackageList(input)

      assertThat(packages).hasSize(2)
      assertThat(packages.map { it.name }).containsExactly("cli", "requests")
    }

    @Test
    fun `package flag returns single member deps`() {
      // Output of: uv tree --depth=1 --frozen --package lib
      val input = """
        lib v0.1.0
        └── pydantic v2.0.0
      """.trimIndent()

      val packages = UvOutputParser.parseUvPackageList(input)

      assertThat(packages).hasSize(1)
      assertThat(packages[0].name).isEqualTo("pydantic")
      assertThat(packages[0].version).isEqualTo("2.0.0")
    }

    @Test
    fun `trailing blank lines are ignored`() {
      val input = "myapp v1.0.0\n├── requests v2.31.0\n\n\n"

      val packages = UvOutputParser.parseUvPackageList(input)

      assertThat(packages).hasSize(1)
      assertThat(packages[0].name).isEqualTo("requests")
    }
  }

  @Nested
  inner class ParseTree {

    @Test
    fun `single root with no children`() {
      val lines = listOf("requests v2.31.0")

      val tree = parseTree(lines)

      assertThat(tree.name.name).isEqualTo("requests")
      assertThat(tree.children).isEmpty()
    }

    @Test
    fun `root with one child`() {
      val lines = listOf(
        "requests v2.31.0",
        "├── urllib3 v2.1.0",
      )

      val tree = parseTree(lines)

      assertThat(tree.name.name).isEqualTo("requests")
      assertThat(tree.children).hasSize(1)
      assertThat(tree.children[0].name.name).isEqualTo("urllib3")
    }

    @Test
    fun `root with multiple children`() {
      val lines = listOf(
        "requests v2.31.0",
        "├── certifi v2024.2.2",
        "├── charset-normalizer v3.3.2",
        "├── idna v3.6",
        "└── urllib3 v2.1.0",
      )

      val tree = parseTree(lines)

      assertThat(tree.name.name).isEqualTo("requests")
      assertThat(tree.children).hasSize(4)
      assertThat(tree.children.map { it.name.name })
        .containsExactly("certifi", "charset-normalizer", "idna", "urllib3")
    }

    @Test
    fun `nested dependencies`() {
      val lines = listOf(
        "flask v3.0.0",
        "├── jinja2 v3.1.3",
        "│   └── markupsafe v2.1.5",
        "└── werkzeug v3.0.1",
        "    └── markupsafe v2.1.5",
      )

      val tree = parseTree(lines)

      assertThat(tree.name.name).isEqualTo("flask")
      assertThat(tree.children).hasSize(2)

      val jinja2 = tree.children[0]
      assertThat(jinja2.name.name).isEqualTo("jinja2")
      assertThat(jinja2.children).hasSize(1)
      assertThat(jinja2.children[0].name.name).isEqualTo("markupsafe")

      val werkzeug = tree.children[1]
      assertThat(werkzeug.name.name).isEqualTo("werkzeug")
      assertThat(werkzeug.children).hasSize(1)
      assertThat(werkzeug.children[0].name.name).isEqualTo("markupsafe")
    }

    @Test
    fun `deeply nested tree`() {
      val lines = listOf(
        "app v1.0.0",
        "└── a v1.0.0",
        "    └── b v1.0.0",
        "        └── c v1.0.0",
      )

      val tree = parseTree(lines)

      assertThat(tree.name.name).isEqualTo("app")
      assertThat(tree.children).hasSize(1)
      assertThat(tree.children[0].name.name).isEqualTo("a")
      assertThat(tree.children[0].children[0].name.name).isEqualTo("b")
      assertThat(tree.children[0].children[0].children[0].name.name).isEqualTo("c")
      assertThat(tree.children[0].children[0].children[0].children).isEmpty()
    }

    @Test
    fun `package with group annotation`() {
      val lines = listOf(
        "myapp v1.0.0",
        "├── pytest v8.0.0 (group: dev)",
        "└── requests v2.31.0",
      )

      val tree = parseTree(lines)

      assertThat(tree.children).hasSize(2)
      assertThat(tree.children[0].name.name).isEqualTo("pytest")
      assertThat(tree.children[0].group).isEqualTo("dev")
      assertThat(tree.children[1].name.name).isEqualTo("requests")
      assertThat(tree.children[1].group).isNull()
    }
  }

  @Nested
  inner class IsRootLine {

    @Test
    fun `package name is root line`() {
      assertThat(TreeParser.isRootLine("requests v2.31.0")).isTrue()
    }

    @Test
    fun `tree branch is not root line`() {
      assertThat(TreeParser.isRootLine("├── requests v2.31.0")).isFalse()
    }

    @Test
    fun `tree corner is not root line`() {
      assertThat(TreeParser.isRootLine("└── requests v2.31.0")).isFalse()
    }

    @Test
    fun `indented line is not root line`() {
      assertThat(TreeParser.isRootLine("    └── markupsafe v2.1.5")).isFalse()
    }

    @Test
    fun `vertical bar is not root line`() {
      assertThat(TreeParser.isRootLine("│   └── markupsafe v2.1.5")).isFalse()
    }

    @Test
    fun `empty line is not root line`() {
      assertThat(TreeParser.isRootLine("")).isFalse()
    }
  }
}
