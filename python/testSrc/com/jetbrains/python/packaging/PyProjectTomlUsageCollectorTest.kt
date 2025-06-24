// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.statistics.PyProjectTomlUsageCollector

class PyProjectTomlStatsTest : PyTestCase() {
  fun doTest(text: String, toolNames: Set<String>, backendNames: Set<String>) {
    val psiFile = myFixture.configureByText(PY_PROJECT_TOML, text)

    val tools = mutableSetOf<String>()
    val backends = mutableSetOf<String>()

    PyProjectTomlUsageCollector.collectTools(psiFile, tools)
    PyProjectTomlUsageCollector.collectBuildBackends(psiFile, backends)

    assertSameElements(tools, toolNames)
    assertSameElements(backends, backendNames)
  }

  fun testEmptyFile() {
    val text = """
        """.trimIndent()

    doTest(text, emptySet(), emptySet())
  }

  fun testInvalidFile() {
    val text = """
      some abradackadabra
      tools.autoflake
      requires=autoflake
        """.trimIndent()

    doTest(text, emptySet(), emptySet())
  }

  fun testEmptyToml() {
    val text = """
        [build-system]
        requires = []
        build-backend = []
      """.trimIndent()

    doTest(text, emptySet(), emptySet())
  }

  fun testDeduplicateTools() {
    val text = """
      [tool.ruff.isort.sections]
      "nextgisweb_env_lib" = ["nextgisweb.env", "nextgisweb.lib"]
      "nextgisweb_comp" = ["nextgisweb"]

      [tool.ruff.flake8-tidy-imports]

      [tool.ruff.flake8-tidy-imports.banned-api]
      pkg_resources.msg = "Consider importlib.metadata or nextgisweb.imptool.module_path"
    """.trimIndent()

    doTest(text, setOf("ruff"), emptySet())
  }

  fun testNormalizeTools() {
    val text = """
      [tool.ruff_furr.isort.sections]
      "nextgisweb_env_lib" = ["nextgisweb.env", "nextgisweb.lib"]
      "nextgisweb_comp" = ["nextgisweb"]

     
    """.trimIndent()

    doTest(text, setOf("ruff-furr"), emptySet())
  }

  fun testCollectBuilds() {
    val text = """
      [build-system]
      requires = ["hatchling", "setuptools >= 61", "flit"]
      build-backend = "xxxxyyy.build"
    """.trimIndent()

    doTest(text, emptySet(), setOf("hatchling", "setuptools", "flit"))
  }

  fun testDeduplicateBuilds() {
    val text = """
      [build-system]
      requires = ["hatchling", "hatchling >= 61", "flit"]
      build-backend = "xxxxyyy.build"
    """.trimIndent()

    doTest(text, emptySet(), setOf("hatchling", "flit"))
  }

  fun testCommentedBuilds() {
    val text = """
      [build-system]
      requires = [
        "hatchling >= 61",
        # "flit",
       ]
      build-backend = "xxxxyyy.build"
    """.trimIndent()

    doTest(text, emptySet(), setOf("hatchling"))
  }

  fun testNormalizeBuilds() {
    val text = """
      [build-system]
      requires = ["flit_core", "setup.tools >= 61", "flit "]
      build-backend = "xxxxyyy.build"
    """.trimIndent()

    doTest(text, emptySet(), setOf("flit", "setup-tools", "flit-core"))
  }

  fun testPyPiSimpleExample() {
    val text = """
      [build-system]
      requires = ["hatchling"]
      build-backend = "xxxxyyy.build"

      [project]
      name = "pypi-simple"
    
      keywords = [
          "...",
      ]

      classifiers = [
          "Development Status :: 5 - Production/Stable",
          "...",
          ]

      dependencies = [
          "beautifulsoup4 ~= 4.5",
          "....",
      ]

      [project.optional-dependencies]
      tqdm = ["tqdm"]

      [tool.hatch.version]
      path = "src/pypi_simple/__init__.py"

      [tool.hatch.envs.default]
      python = "3"

      [tool.mypy]
      plugins = ["pydantic.mypy"]

      [tool.pydantic-mypy]
      init_forbid_extra = true
    """.trimIndent()

    doTest(text, setOf("hatch", "mypy", "pydantic-mypy"), setOf("hatchling"))
  }

  fun testPyProjectExample() {
    val text = """
      [build-system]
      build-backend = "setuptools.build_meta"
      requires = [
        "setuptools >= 61",
      ]

      [project]
      name = "wavelet_prosody_toolkit"
   
      dependencies = [
        "pyyaml",
        "...",
      ]

      [project.optional-dependencies]
      dev = ["pre-commit"]

      [tool.setuptools]
      packages = ["wavelet_prosody_toolkit"]

      [tool.black]
      line-length = 120

      [tool.flake8]
      max-line-length = 120

      [tool.basedpyright]
      typeCheckingMode = "standard"
    """.trimIndent()

    doTest(text, setOf("setuptools", "black", "flake8", "basedpyright"), setOf("setuptools"))
  }
}