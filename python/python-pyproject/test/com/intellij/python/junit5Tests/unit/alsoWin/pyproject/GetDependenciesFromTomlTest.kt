package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.idea.TestFor
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.internal.pyProjectToml.getDependenciesFromToml
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.TomlDependencySpecification
import com.intellij.testFramework.common.timeoutRunBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories

internal class GetDependenciesFromTomlTest {

  private data class TestProject(
    override val pyProjectToml: PyProjectToml,
    override val root: Path,
  ) : PyProjectTomlProject

  @Test
  fun `pep 621 project dependencies`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val mainDir = tempDir.resolve("main").createDirectories()
    val libDir = tempDir.resolve("lib").createDirectories()

    val libUri = libDir.toUri()
    val toml = PyProjectToml.parse(tomlFileContent = """
          [project]
          name = "main"
          version = "1.0"
          dependencies = ["lib @ $libUri"]
        """.trimIndent(), "someProject")!!

    val mainName = ProjectName("main")
    val libName = ProjectName("lib")

    val entries = mapOf(mainName to TestProject(toml, mainDir))
    val rootIndex = mapOf(libDir to libName)

    val result = getDependenciesFromToml(entries, rootIndex, emptyList())
    assertThat(result.map[mainName]).containsExactly(libName)
  }

  @Test
  @TestFor(issues = ["PY-90798"])
  fun `hatch context formatted project dependencies`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val workspaceDir = tempDir.resolve("workspace")
    val packagesDir = workspaceDir.resolve("packages")
    val mainDir = packagesDir.resolve("main").createDirectories()
    val childDir = mainDir.resolve("child").createDirectories()
    val siblingDir = packagesDir.resolve("sibling").createDirectories()
    val sharedDir = workspaceDir.resolve("shared").createDirectories()

    val toml = PyProjectToml.parse(tomlFileContent = """
          [project]
          name = "main"
          version = "1.0"
          dependencies = [
            "child @ {root:uri}/child",
            "sibling @ {root:parent:uri}/sibling",
            "shared @ {root:parent:parent:uri}/shared",
          ]
        """.trimIndent(), "someProject")!!

    val mainName = ProjectName("main")
    val childName = ProjectName("child")
    val siblingName = ProjectName("sibling")
    val sharedName = ProjectName("shared")
    val entries = mapOf(mainName to TestProject(toml, mainDir))
    val rootIndex = mapOf(childDir to childName, siblingDir to siblingName, sharedDir to sharedName)

    val result = getDependenciesFromToml(entries, rootIndex, emptyList())
    assertThat(result.map[mainName]).containsExactlyInAnyOrder(childName, siblingName, sharedName)
  }

  @Test
  fun `pep 735 dependency groups`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val mainDir = tempDir.resolve("main").createDirectories()
    val libDir = tempDir.resolve("lib").createDirectories()
    val testLibDir = tempDir.resolve("test-lib").createDirectories()

    val libUri = libDir.toUri()
    val testLibUri = testLibDir.toUri()
    val toml = PyProjectToml.parse(tomlFileContent = """
          [project]
          name = "main"
          version = "1.0"
          
          [dependency-groups]
          dev = ["lib @ $libUri"]
          test = ["test-lib @ $testLibUri"]
        """.trimIndent(), "someProject")!!

    val mainName = ProjectName("main")
    val libName = ProjectName("lib")
    val testLibName = ProjectName("test-lib")

    val entries = mapOf(mainName to TestProject(toml, mainDir))
    val rootIndex = mapOf(libDir to libName, testLibDir to testLibName)

    val result = getDependenciesFromToml(entries, rootIndex, emptyList())
    assertThat(result.map[mainName]).containsExactlyInAnyOrder(libName, testLibName)
  }

  @Test
  fun `tool PathDependency`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val mainDir = tempDir.resolve("main").createDirectories()
    val libDir = tempDir.resolve("lib").createDirectories()

    val toml = PyProjectToml.parse(tomlFileContent = """
          [project]
          name = "main"
          version = "1.0"
          
          [tool.poetry.dependencies]
          lib = {path = "../lib"}
        """.trimIndent(), "someProject")!!

    val mainName = ProjectName("main")
    val libName = ProjectName("lib")

    val entries = mapOf(mainName to TestProject(toml, mainDir))
    val rootIndex = mapOf(libDir to libName)
    val specs = listOf(TomlDependencySpecification.PathDependency("tool.poetry.dependencies"))

    val result = getDependenciesFromToml(entries, rootIndex, specs)
    assertThat(result.map[mainName]).containsExactly(libName)
  }

  @Test
  @TestFor(issues = ["PY-91195"])
  @Disabled(
    "PY-91195: getToolSpecificDependenciesFromTomlTable reads `<dep>.path` as a single string, so an " +
    "array-of-tables value is dropped and `main` ends up with no dependency at all. The array-aware " +
    "half of the fix landed only in UvPyProjectManager#getUvDependencies, which runs for workspace " +
    "members only. Enable this test with the fix; it needs no changes of its own."
  )
  fun `tool PathDependency as an array of tables`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val mainDir = tempDir.resolve("main").createDirectories()
    val libLinuxDir = tempDir.resolve("lib-linux").createDirectories()
    val libWinDir = tempDir.resolve("lib-win").createDirectories()

    val toml = PyProjectToml.parse("""
      [project]
      name = "main"
      version = "1.0"

      [tool.uv.sources]
      lib = [
          { path = "../lib-linux", marker = "sys_platform == 'linux'" },
          { path = "../lib-win", marker = "sys_platform == 'win32'" },
      ]
    """.trimIndent())!!

    val mainName = ProjectName("main")
    val libLinuxName = ProjectName("lib-linux")
    val libWinName = ProjectName("lib-win")

    val entries = mapOf(mainName to TestProject(toml, mainDir))
    val rootIndex = mapOf(libLinuxDir to libLinuxName, libWinDir to libWinName)
    val specs = listOf(TomlDependencySpecification.PathDependency("tool.uv.sources"))

    val result = getDependenciesFromToml(entries, rootIndex, specs)
    // Exactness rather than containment: the defect drops array elements, so a containment check would
    // also be satisfied by a fix that honours only the first table in the array.
    assertThat(result.map[mainName]).containsExactlyInAnyOrder(libLinuxName, libWinName)
  }

  @Test
  fun `tool Pep621Dependency`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val mainDir = tempDir.resolve("main").createDirectories()
    val libDir = tempDir.resolve("lib").createDirectories()

    val libUri = libDir.toUri()
    val toml = PyProjectToml.parse(tomlFileContent = """
          [project]
          name = "main"
          version = "1.0"
          
          [tool.uv]
          dev-dependencies = ["lib @ $libUri"]
        """.trimIndent(), "someProject")!!

    val mainName = ProjectName("main")
    val libName = ProjectName("lib")

    val entries = mapOf(mainName to TestProject(toml, mainDir))
    val rootIndex = mapOf(libDir to libName)
    val specs = listOf(TomlDependencySpecification.Pep621Dependency("tool.uv.dev-dependencies"))

    val result = getDependenciesFromToml(entries, rootIndex, specs)
    assertThat(result.map[mainName]).containsExactly(libName)
  }

  @Test
  fun `tool GroupPathDependency`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val mainDir = tempDir.resolve("main").createDirectories()
    val libDir = tempDir.resolve("lib").createDirectories()

    val toml = PyProjectToml.parse(tomlFileContent = """
          [project]
          name = "main"
          version = "1.0"
          
          [tool.poetry.group.test.dependencies]
          lib = {path = "../lib"}
        """.trimIndent(), "someProject")!!

    val mainName = ProjectName("main")
    val libName = ProjectName("lib")

    val entries = mapOf(mainName to TestProject(toml, mainDir))
    val rootIndex = mapOf(libDir to libName)
    val specs = listOf(TomlDependencySpecification.GroupPathDependency("tool.poetry.group", "dependencies"))

    val result = getDependenciesFromToml(entries, rootIndex, specs)
    assertThat(result.map[mainName]).containsExactly(libName)
  }

  @Test
  @TestFor(issues = ["PY-90798"])
  fun `tool GroupPep621Dependency`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val workspaceDir = tempDir.resolve("workspace")
    val mainDir = workspaceDir.resolve("main").createDirectories()
    val libDir = workspaceDir.resolve("lib").createDirectories()

    val toml = PyProjectToml.parse(tomlFileContent = """
          [project]
          name = "main"
          version = "1.0"
    
          [tool.hatch.envs.default]
          dependencies = [
            "pytest>=8",
            "lib @ {root:parent:uri}/lib",
          ]
        """.trimIndent(), "someProject")!!

    val mainName = ProjectName("main")
    val libName = ProjectName("lib")
    val entries = mapOf(mainName to TestProject(toml, mainDir))
    val rootIndex = mapOf(libDir to libName)
    val specs = listOf(TomlDependencySpecification.GroupPep621Dependency("tool.hatch.envs", "dependencies"))

    val result = getDependenciesFromToml(entries, rootIndex, specs)
    assertThat(result.map[mainName]).containsExactly(libName)
  }

  @Test
  fun `non-path dependencies are ignored`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val mainDir = tempDir.resolve("main").createDirectories()

    val toml = PyProjectToml.parse(tomlFileContent = """
          [project]
          name = "main"
          version = "1.0"
          dependencies = ["pytest>=7", "requests"]
          
          [dependency-groups]
          dev = ["black>=23"]
        """.trimIndent(), "someProject")!!

    val mainName = ProjectName("main")
    val entries = mapOf(mainName to TestProject(toml, mainDir))

    val result = getDependenciesFromToml(entries, emptyMap(), emptyList())
    assertThat(result.map[mainName]).isEmpty()
  }

  @Test
  fun `empty and missing sections`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val mainDir = tempDir.resolve("main").createDirectories()

    val toml = PyProjectToml.parse(tomlFileContent = """
          [project]
          name = "main"
          version = "1.0"
        """.trimIndent(), "someProject")!!

    val mainName = ProjectName("main")
    val entries = mapOf(mainName to TestProject(toml, mainDir))
    val specs = listOf(
      TomlDependencySpecification.PathDependency("tool.poetry.dependencies"),
      TomlDependencySpecification.Pep621Dependency("tool.uv.dev-dependencies"),
      TomlDependencySpecification.GroupPathDependency("tool.poetry.group", "dependencies"),
    )

    val result = getDependenciesFromToml(entries, emptyMap(), specs)
    assertThat(result.map[mainName]).isEmpty()
  }

  @Test
  fun `mixed sources collected together`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val mainDir = tempDir.resolve("main").createDirectories()
    val pep621Lib = tempDir.resolve("pep621-lib").createDirectories()
    val groupLib = tempDir.resolve("group-lib").createDirectories()
    val toolLib = tempDir.resolve("tool-lib").createDirectories()

    val pep621Uri = pep621Lib.toUri()
    val groupUri = groupLib.toUri()
    val toml = PyProjectToml.parse(tomlFileContent = """
          [project]
          name = "main"
          version = "1.0"
          dependencies = ["pep621-lib @ $pep621Uri"]
          
          [dependency-groups]
          dev = ["group-lib @ $groupUri"]
          
          [tool.poetry.dependencies]
          tool-lib = {path = "../tool-lib"}
        """.trimIndent(), "someProject")!!

    val mainName = ProjectName("main")
    val pep621Name = ProjectName("pep621-lib")
    val groupName = ProjectName("group-lib")
    val toolName = ProjectName("tool-lib")

    val entries = mapOf(mainName to TestProject(toml, mainDir))
    val rootIndex = mapOf(pep621Lib to pep621Name, groupLib to groupName, toolLib to toolName)
    val specs = listOf(TomlDependencySpecification.PathDependency("tool.poetry.dependencies"))

    val result = getDependenciesFromToml(entries, rootIndex, specs)
    assertThat(result.map[mainName]).containsExactlyInAnyOrder(pep621Name, groupName, toolName)
  }
}
