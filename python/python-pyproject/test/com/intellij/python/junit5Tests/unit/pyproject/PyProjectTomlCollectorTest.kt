// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pyproject

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.statistics.DEPENDENCY_GROUP_OTHER
import com.intellij.python.pyproject.statistics.PyProjectTomlCollector
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import org.toml.lang.psi.TomlFileType

@PyDefaultTestApplication
class PyProjectTomlCollectorTest(val project: Project) {
  fun doTest(
    text: String,
    toolNames: Set<String> = emptySet(),
    backendNames: Set<String> = emptySet(),
    dependencyGroupsNames: Set<String> = emptySet(),
  ) = timeoutRunBlocking(context = Dispatchers.EDT) {
    val psiFile = PsiFileFactory.getInstance(project).createFileFromText(PY_PROJECT_TOML, TomlFileType, text)

    val tools = PyProjectTomlCollector.findDeclaredTools(psiFile).map { it.fusName }
    val backends = PyProjectTomlCollector.findBuildSystemRequiresTools(psiFile).map { it.fusName }
    val dependencyGroups = PyProjectTomlCollector.findDependencyGroups(psiFile)

    UsefulTestCase.assertSameElements(tools, toolNames)
    UsefulTestCase.assertSameElements(backends, backendNames)
    UsefulTestCase.assertSameElements(dependencyGroups, dependencyGroupsNames)
  }

  @Test
  fun testDependencyGroups() {
    val text = """
      [dependency-groups]
      test = ["pytest<8", "coverage"]
      typing = ["mypy==1.7.1", "types-requests"]
      
      [dependency-groups]
      lint = ["black", "flake8"]
      typing-test = [{include-group = "typing"}, "pytest<8"]
      """.trimIndent()
    doTest(text, dependencyGroupsNames = setOf("test", "typing", "lint", DEPENDENCY_GROUP_OTHER))
  }

  @Test
  fun testEmptyFile() {
    val text = """
        """.trimIndent()

    doTest(text, emptySet(), emptySet())
  }

  @Test
  fun testInvalidFile() {
    val text = """
      some abradackadabra
      tools.autoflake
      requires=autoflake
        """.trimIndent()

    doTest(text, emptySet(), emptySet())
  }

  @Test
  fun testEmptyToml() {
    val text = """
        [build-system]
        requires = []
        build-backend = []
      """.trimIndent()

    doTest(text, emptySet(), emptySet())
  }

  @Test
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

  @Test
  fun testNormalizeTools() {
    val text = """
      [tool.py_spy.isort.sections]
      "nextgisweb_env_lib" = ["nextgisweb.env", "nextgisweb.lib"]
      "nextgisweb_comp" = ["nextgisweb"]

     
    """.trimIndent()

    doTest(text, setOf("py-spy"), emptySet())
  }

  @Test
  fun testCollectBuilds() {
    val text = """
      [build-system]
      requires = ["hatchling", "setuptools >= 61", "flit"]
      build-backend = "xxxxyyy.build"
    """.trimIndent()

    doTest(text, emptySet(), setOf("hatchling", "setuptools", "flit"))
  }

  @Test
  fun testDeduplicateBuilds() {
    val text = """
      [build-system]
      requires = ["hatchling", "hatchling >= 61", "flit"]
      build-backend = "xxxxyyy.build"
    """.trimIndent()

    doTest(text, emptySet(), setOf("hatchling", "flit"))
  }

  @Test
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

  @Test
  fun testNormalizeBuilds() {
    val text = """
      [build-system]
      requires = ["flit_core", "setuptools >= 61", "flit "]
      build-backend = "xxxxyyy.build"
    """.trimIndent()

    doTest(text, emptySet(), setOf("flit", "setuptools", "flit-core"))
  }

  @Test
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

  @Test
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