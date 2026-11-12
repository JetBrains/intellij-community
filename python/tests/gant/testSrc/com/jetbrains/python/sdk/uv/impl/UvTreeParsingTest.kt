// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.impl

import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PyDependencyGroupName
import com.jetbrains.python.packaging.packageRequirements.PackageTreeNode
import com.jetbrains.python.packaging.packageRequirements.TreeParser
import com.jetbrains.python.packaging.packageRequirements.TreeParser.parseTrees
import com.jetbrains.python.packaging.packageRequirements.collectAllNames
import com.jetbrains.python.packaging.packageRequirements.extractDeclaredDependencies
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@Subsystems.Interpreters
@Components.Uv
@Layers.Functional
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
    fun `dependency groups are parsed from group annotation`() {
      val input = """
        myapp v1.0.0
        ├── requests v2.31.0
        ├── pytest v8.0.0 (group: dev)
        ├── ruff v0.15.2 (group: lint)
        └── ty v0.0.18 (group: lint)
      """.trimIndent()

      val packages = UvOutputParser.parseUvPackageList(input)

      assertThat(packages).hasSize(4)
      val byName = packages.associateBy { it.name }
      assertThat(byName["requests"]!!.dependencyGroup).isNull()
      assertThat(byName["pytest"]!!.dependencyGroup).isEqualTo(PyDependencyGroupName("dev"))
      assertThat(byName["ruff"]!!.dependencyGroup).isEqualTo(PyDependencyGroupName("lint"))
      assertThat(byName["ty"]!!.dependencyGroup).isEqualTo(PyDependencyGroupName("lint"))
    }

    @Test
    fun `dependency groups are parsed from extra annotation`() {
      val input = """
        myapp v1.0.0
        ├── fastapi v0.129.2
        ├── pytest-cov v7.0.0 (extra: dev)
        ├── ruff v0.15.2 (extra: lint)
        └── ty v0.0.18 (extra: lint)
      """.trimIndent()

      val packages = UvOutputParser.parseUvPackageList(input)

      assertThat(packages).hasSize(4)
      val byName = packages.associateBy { it.name }
      assertThat(byName["fastapi"]!!.dependencyGroup).isNull()
      assertThat(byName["pytest-cov"]!!.dependencyGroup).isEqualTo(PyDependencyGroupName("dev"))
      assertThat(byName["ruff"]!!.dependencyGroup).isEqualTo(PyDependencyGroupName("lint"))
      assertThat(byName["ty"]!!.dependencyGroup).isEqualTo(PyDependencyGroupName("lint"))
    }

    @Test
    fun `extras are stripped from package names`() {
      val input = """
        myapp v1.0.0
        ├── uvicorn[standard] v0.41.0
        └── boto3[crt] v1.35.0
      """.trimIndent()

      val packages = UvOutputParser.parseUvPackageList(input)

      assertThat(packages).hasSize(2)
      assertThat(packages.map { it.name }).containsExactly("uvicorn", "boto3")
      assertThat(packages.map { it.version }).containsExactly("0.41.0", "1.35.0")
    }

    @Test
    fun `extras with spaces are handled correctly`() {
      val input = """
        repro v0.1.0
        ├── faststream[cli, nats, otel] v0.6.7
        ├── django-allauth[mfa, socialaccount] v65.14.3
        ├── psycopg[binary, pool] v3.3.3
        └── taskiq[metrics, orjson, reload] v0.12.1
      """.trimIndent()

      val packages = UvOutputParser.parseUvPackageList(input)

      assertThat(packages).hasSize(4)
      assertThat(packages.map { it.name }).containsExactly("faststream", "django-allauth", "psycopg", "taskiq")
      assertThat(packages.map { it.version }).containsExactly("0.6.7", "65.14.3", "3.3.3", "0.12.1")
    }

    @Test
    fun `extras with spaces and groups are handled correctly`() {
      val input = """
        myapp v1.0.0
        ├── faststream[cli, nats, otel] v0.6.7
        └── pytest-cov[extra1, extra2] v7.0.0 (group: dev)
      """.trimIndent()

      val packages = UvOutputParser.parseUvPackageList(input)

      assertThat(packages).hasSize(2)
      assertThat(packages.map { it.name }).containsExactly("faststream", "pytest-cov")
      assertThat(packages.map { it.version }).containsExactly("0.6.7", "7.0.0")
      assertThat(packages[0].dependencyGroup).isNull()
      assertThat(packages[1].dependencyGroup).isEqualTo(PyDependencyGroupName("dev"))
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

      val tree = parseTrees(lines).first()

      assertThat(tree.name.name).isEqualTo("requests")
      assertThat(tree.version).isEqualTo("2.31.0")
      assertThat(tree.children).isEmpty()
    }

    @Test
    fun `root with one child`() {
      val lines = listOf(
        "requests v2.31.0",
        "├── urllib3 v2.1.0",
      )

      val tree = parseTrees(lines).first()

      assertThat(tree.name.name).isEqualTo("requests")
      assertThat(tree.version).isEqualTo("2.31.0")
      assertThat(tree.children).hasSize(1)
      assertThat(tree.children[0].name.name).isEqualTo("urllib3")
      assertThat(tree.children[0].version).isEqualTo("2.1.0")
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

      val tree = parseTrees(lines).first()

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

      val tree = parseTrees(lines).first()

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

      val tree = parseTrees(lines).first()

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

      val tree = parseTrees(lines).first()

      assertThat(tree.children).hasSize(2)
      assertThat(tree.children[0].name.name).isEqualTo("pytest")
      assertThat(tree.children[0].group).isEqualTo("dev")
      assertThat(tree.children[0].version).isEqualTo("8.0.0")
      assertThat(tree.children[1].name.name).isEqualTo("requests")
      assertThat(tree.children[1].group).isNull()
    }

    @Test
    fun `poetry-style versions without v prefix`() {
      val lines = listOf(
        "flask 3.0.0 A micro web framework",
        "├── jinja2 3.1.3",
        "└── werkzeug 3.0.1",
      )

      val tree = parseTrees(lines).first()

      assertThat(tree.name.name).isEqualTo("flask")
      assertThat(tree.version).isEqualTo("3.0.0")
      assertThat(tree.children[0].version).isEqualTo("3.1.3")
      assertThat(tree.children[1].version).isEqualTo("3.0.1")
    }

    @Test
    fun `version extracted correctly for extras with spaces`() {
      val lines = listOf(
        "myapp v1.0.0",
        "├── faststream[cli, nats, otel] v0.6.7",
        "└── uvicorn[standard] v0.41.0",
      )

      val tree = parseTrees(lines).first()

      assertThat(tree.children[0].name.name).isEqualTo("faststream")
      assertThat(tree.children[0].version).isEqualTo("0.6.7")
      assertThat(tree.children[1].name.name).isEqualTo("uvicorn")
      assertThat(tree.children[1].version).isEqualTo("0.41.0")
    }
  }

  @Nested
  inner class ParseWorkspaceTrees {

    @Test
    fun `workspace output produces one PackageTreeNode per member`() {
      val lines = listOf(
        "monorepo v0.1.0",
        "├── cli v0.1.0",
        "├── requests v2.31.0",
        "│   └── urllib3 v2.1.0",
        "cli v0.1.0",
        "├── lib v0.1.0",
        "├── click v8.1.0",
        "lib v0.1.0",
        "└── pydantic v2.0.0",
      )

      val trees = parseTrees(lines)

      assertThat(trees).hasSize(3)
      assertThat(trees.map { it.name.name }).containsExactly("monorepo", "cli", "lib")
    }

    @Test
    fun `workspace members can be indexed by name`() {
      val lines = listOf(
        "monorepo v0.1.0",
        "├── cli v0.1.0",
        "├── requests v2.31.0",
        "cli v0.1.0",
        "├── click v8.1.0",
        "lib v0.1.0",
        "└── pydantic v2.0.0",
      )

      val byName = parseTrees(lines).associateBy { it.name.name }

      assertThat(byName).containsKeys("monorepo", "cli", "lib")
      assertThat(byName["monorepo"]!!.children.map { it.name.name }).containsExactly("cli", "requests")
      assertThat(byName["cli"]!!.children.map { it.name.name }).containsExactly("click")
      assertThat(byName["lib"]!!.children.map { it.name.name }).containsExactly("pydantic")
    }

    @Test
    fun `workspace with blank lines between sections`() {
      val lines = listOf(
        "root v1.0.0",
        "├── numpy v1.26.0",
        "",
        "sub-a v0.1.0",
        "├── pandas v2.0.0",
        "",
        "sub-b v0.1.0",
        "└── scipy v1.12.0",
      )

      val trees = parseTrees(lines)

      assertThat(trees).hasSize(3)
      assertThat(trees.map { it.name.name }).containsExactly("root", "sub-a", "sub-b")
      assertThat(trees[0].children.map { it.name.name }).containsExactly("numpy")
      assertThat(trees[1].children.map { it.name.name }).containsExactly("pandas")
      assertThat(trees[2].children.map { it.name.name }).containsExactly("scipy")
    }

    @Test
    fun `workspace member with nested dependencies`() {
      val lines = listOf(
        "root v1.0.0",
        "├── sub v0.1.0",
        "sub v0.1.0",
        "├── flask v3.0.0",
        "│   └── jinja2 v3.1.3",
        "└── click v8.1.0",
      )

      val byName = parseTrees(lines).associateBy { it.name.name }

      assertThat(byName["sub"]!!.children).hasSize(2)
      val flask = byName["sub"]!!.children[0]
      assertThat(flask.name.name).isEqualTo("flask")
      assertThat(flask.children).hasSize(1)
      assertThat(flask.children[0].name.name).isEqualTo("jinja2")
    }

    @Test
    fun `workspace member with no dependencies`() {
      val lines = listOf(
        "root v1.0.0",
        "├── requests v2.31.0",
        "empty-member v0.1.0",
      )

      val byName = parseTrees(lines).associateBy { it.name.name }

      assertThat(byName).containsKeys("root", "empty-member")
      assertThat(byName["empty-member"]!!.children).isEmpty()
    }
  }

  @Nested
  inner class ExtractDeclaredDependencies {

    @Test
    fun `extracts depth-1 packages from all workspace members`() {
      val input = """
        monorepo v0.1.0
        ├── cli v0.1.0
        ├── requests v2.31.0
        │   └── urllib3 v2.1.0
        └── flask v3.0.0
            └── jinja2 v3.1.3
        cli v0.1.0
        ├── click v8.1.0
        │   └── colorama v0.4.6
        └── lib v0.1.0
        lib v0.1.0
        └── pydantic v2.0.0
      """.trimIndent()

      val trees = parseTrees(input.lines())
      val packages = extractDeclaredDependencies(trees)

      assertThat(packages.map { it.name }).containsExactlyInAnyOrder("cli", "requests", "flask", "click", "lib", "pydantic")
      val byName = packages.associateBy { it.name }
      assertThat(byName["requests"]!!.version).isEqualTo("2.31.0")
      assertThat(byName["click"]!!.version).isEqualTo("8.1.0")
      assertThat(byName["pydantic"]!!.version).isEqualTo("2.0.0")
    }

    @Test
    fun `skips transitive dependencies`() {
      val input = """
        myapp v1.0.0
        ├── flask v3.0.0
        │   ├── jinja2 v3.1.3
        │   │   └── markupsafe v2.1.5
        │   └── werkzeug v3.0.1
        └── requests v2.31.0
            └── urllib3 v2.1.0
      """.trimIndent()

      val trees = parseTrees(input.lines())
      val packages = extractDeclaredDependencies(trees)

      assertThat(packages.map { it.name }).containsExactly("flask", "requests")
    }

    @Test
    fun `handles single non-workspace project`() {
      val input = """
        myapp v1.0.0
        ├── requests v2.31.0
        └── flask v3.0.0
      """.trimIndent()

      val trees = parseTrees(input.lines())
      val packages = extractDeclaredDependencies(trees)

      assertThat(packages).hasSize(2)
      assertThat(packages.map { it.name }).containsExactly("requests", "flask")
    }

    @Test
    fun `deduplicates packages across members`() {
      val input = """
        root v1.0.0
        ├── shared-lib v1.0.0
        sub v0.1.0
        └── shared-lib v1.0.0
      """.trimIndent()

      val trees = parseTrees(input.lines())
      val packages = extractDeclaredDependencies(trees)

      assertThat(packages.map { it.name }).containsExactly("shared-lib")
    }

    @Test
    fun `preserves dependency groups`() {
      val input = """
        myapp v1.0.0
        ├── requests v2.31.0
        └── pytest v8.0.0 (group: dev)
      """.trimIndent()

      val trees = parseTrees(input.lines())
      val packages = extractDeclaredDependencies(trees)

      assertThat(packages).hasSize(2)
      val byName = packages.associateBy { it.name }
      assertThat(byName["requests"]!!.dependencyGroup).isNull()
      assertThat(byName["pytest"]!!.dependencyGroup).isEqualTo(PyDependencyGroupName("dev"))
    }
  }

  /**
   * `uv tree` prints a package's dependencies under the first parent that needs it and marks every
   * later line for it with `(*)`. The parser gives all those parents the same node, so the marked
   * line carries the dependencies too (PY-90174).
   */
  @Nested
  inner class DeduplicatedOutput {

    @Test
    fun `a marked line is the same node as the expanded one`() {
      val input = """
        myapp v1.0.0
        ├── fastapi[standard] v0.141.1
        │   └── uvicorn[standard] v0.52.4
        │       ├── httptools v0.7.1
        │       └── uvloop v0.22.1
        └── uvicorn[standard] v0.52.4 (*)
        (*) Package tree already displayed
      """.trimIndent()

      val root = parseTrees(input.lines()).single()
      val expanded = root.children[0].children.single()
      val marked = root.children[1]

      assertThat(marked).isSameAs(expanded)
      assertThat(marked.children.map { it.name.name }).containsExactly("httptools", "uvloop")
    }

    @Test
    fun `a marked line keeps its own group`() {
      // `sphinxcontrib-jquery` is only ever declared through the docs extra, but the tool prints it first as a plain
      // dependency of something deeper. Sharing that line's group made it look like a dependency the environment must
      // hold, and it was reported missing whenever the extra was not installed (PY-90174).
      val input = """
        devel-common v0.1.0
        ├── sphinx v8.0.0
        │   └── sphinxcontrib-jquery v4.1
        └── sphinxcontrib-jquery v4.1 (extra: docs) (*)
      """.trimIndent()

      val root = parseTrees(input.lines()).single()
      val declaring = root.children[1]

      assertThat(declaring.group).isEqualTo("docs")
      assertThat(extractDeclaredDependencies(listOf(root)).single { it.name == "sphinxcontrib-jquery" }.dependencyGroup)
        .isEqualTo(PyDependencyGroupName("docs"))
    }

    @Test
    fun `a marked line shares the dependencies of the line it repeats`() {
      val input = """
        myapp v1.0.0
        ├── uvicorn v0.52.4
        │   └── httptools v0.7.1
        └── uvicorn v0.52.4 (group: dev) (*)
      """.trimIndent()

      val root = parseTrees(input.lines())

      val declaring = root.single().children[1]
      assertThat(declaring.group).isEqualTo("dev")
      assertThat(declaring.children.map { it.name.name }).containsExactly("httptools")
    }

    @Test
    fun `a workspace member marked at its own root keeps its dependencies`() {
      val input = """
        apache-airflow v3.4.0
        └── apache-airflow-core v3.4.0
            └── cfn-lint v1.41.0
        apache-airflow-core v3.4.0 (*)
      """.trimIndent()

      val roots = parseTrees(input.lines()).associateBy { it.name.name }

      assertThat(roots.getValue("apache-airflow-core").children.map { it.name.name }).containsExactly("cfn-lint")
      assertThat(extractDeclaredDependencies(parseTrees(input.lines())).map { it.name })
        .containsExactlyInAnyOrder("apache-airflow-core", "cfn-lint")
    }

    @Test
    fun `a package and its extra stay separate nodes`() {
      // uv resolves `pkg` and `pkg[extra]` to different dependencies, so they must not share a node.
      val input = """
        myapp v1.0.0
        ├── sqlalchemy v2.0.52
        └── sqlalchemy[asyncio] v2.0.52
            └── greenlet v3.2.4
      """.trimIndent()

      val root = parseTrees(input.lines()).single()

      assertThat(root.children[0].children).isEmpty()
      assertThat(root.children[1].children.map { it.name.name }).containsExactly("greenlet")
    }

    @Test
    fun `the trailing legend is not a package`() {
      val legends = listOf("(*) Package tree already displayed", "(*) Package tree is a cycle and cannot be shown")

      for (legend in legends) {
        val input = """
          myapp v1.0.0
          └── flask v3.0.0 (*)
          $legend
        """.trimIndent()

        assertThat(parseTrees(input.lines()).map { it.name.name }).describedAs(legend).containsExactly("myapp")
      }
    }

    @Test
    fun `the marker is not read as a version`() {
      val input = """
        myapp v1.0.0
        └── local-member (*)
      """.trimIndent()

      val member = parseTrees(input.lines()).single().children.single()

      assertThat(member.name).isEqualTo(PyPackageName.from("local-member"))
      assertThat(member.version).isNull()
    }

    @Test
    fun `a cycle does not stop the walk from ending`() {
      // uv uses the same marker to close a cycle, so the graph the parser builds can have one.
      val input = """
        myapp v1.0.0
        └── a v1.0.0
            └── b v1.0.0
                └── a v1.0.0 (*)
      """.trimIndent()

      val root = parseTrees(input.lines()).single()

      val a = root.children.single()
      assertThat(a.children.single().children.single()).isSameAs(a)
      assertThat(root.collectAllNames()).containsExactlyInAnyOrder("myapp", "a", "b")
    }
  }

  @Nested
  inner class CollectAllNames {

    private fun node(name: String, vararg children: PackageTreeNode): PackageTreeNode =
      PackageTreeNode(PyPackageName.from(name), children.toMutableList())

    @Test
    fun `single node`() {
      val root = node("a")
      assertThat(root.collectAllNames()).containsExactly("a")
    }

    @Test
    fun `tree with duplicates`() {
      val root = node("a",
        node("b", node("d")),
        node("c", node("d")),
      )
      assertThat(root.collectAllNames()).containsExactlyInAnyOrder("a", "b", "c", "d")
    }

    @Test
    fun `cycle does not cause infinite loop`() {
      val a = node("a")
      val b = node("b")
      val c = node("c")
      a.children.add(b)
      b.children.add(c)
      c.children.add(a)

      assertThat(a.collectAllNames()).containsExactlyInAnyOrder("a", "b", "c")
    }

    @Test
    fun `wide tree with many children`() {
      val children = (1..1000).map { node("pkg-$it") }.toMutableList()
      val root = PackageTreeNode(PyPackageName.from("root"), children)
      val names = root.collectAllNames()
      assertThat(names).hasSize(1001)
      assertThat(names).contains("root", "pkg-1", "pkg-500", "pkg-1000")
    }

    @Test
    fun `deep tree with heavy duplication`() {
      // Simulates --no-dedupe: each level repeats the same subtree
      val shared = node("shared", node("leaf-1"), node("leaf-2"))
      val root = node("root",
        node("a", shared),
        node("b", shared),
        node("c", shared),
      )
      assertThat(root.collectAllNames()).containsExactlyInAnyOrder(
        "root", "a", "b", "c", "shared", "leaf-1", "leaf-2"
      )
    }

    @Test
    fun `large graph with multiple cycles, self-loops, and heavy duplication`() {
      // Build a wide + deep graph that exercises deduplication and cycle handling:
      //
      //  root ──► branch-0..9 ──► mid-0..9 ──► leaf-0..4
      //               │                │              │
      //               └──► shared ◄────┘              │
      //                     │  ▲                      │
      //                     └──┘ (self-loop)          │
      //                     │                         │
      //                     └──► deep-0..2 ──► root (cycle back to root)
      //
      //  Every mid-N also points back to branch-((N+1) % 10) forming inter-branch cycles.

      val shared = node("shared")
      val deepNodes = (0..2).map { node("deep-$it") }
      shared.children.addAll(deepNodes)
      shared.children.add(shared) // self-loop

      val root = node("root")
      deepNodes.forEach { it.children.add(root) } // cycles back to root

      val leaves = (0..4).map { node("leaf-$it") }
      val branches = (0..9).map { i ->
        val mids = (0..9).map { j ->
          val mid = node("mid-$i-$j")
          mid.children.addAll(leaves)      // every mid references same leaf nodes
          mid.children.add(shared)          // every mid references shared
          mid
        }
        val branch = node("branch-$i")
        branch.children.addAll(mids)
        branch.children.add(shared)         // direct branch → shared too
        branch
      }

      // inter-branch cycles: mid-i-0 → branch-((i+1)%10)
      for (i in 0..9) {
        val mid0 = branches[i].children[0] // mid-i-0
        mid0.children.add(branches[(i + 1) % 10])
      }

      root.children.addAll(branches)

      val names = root.collectAllNames()

      // Expected: root + 10 branches + 100 mids + 5 leaves + shared + 3 deep = 120
      val expectedNames = mutableSetOf("root", "shared")
      (0..9).forEach { i ->
        expectedNames.add("branch-$i")
        (0..9).forEach { j -> expectedNames.add("mid-$i-$j") }
      }
      (0..4).forEach { expectedNames.add("leaf-$it") }
      (0..2).forEach { expectedNames.add("deep-$it") }

      assertThat(names).containsExactlyInAnyOrderElementsOf(expectedNames)
      assertThat(names).hasSize(120)
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
