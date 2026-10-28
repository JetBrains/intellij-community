package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.python.sdk.backend.setPyInterpreterDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.python.sdk.backend.PyInterpreterLibraryName
import com.intellij.python.sdk.backend.PyInterpreterOwnerDir
import com.intellij.python.sdk.backend.pyInterpreterDependency
import com.intellij.python.pytools.common.FusId
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.sdk.common.PyInterpreterRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Path

class PyInterpreterLibraryNameTest {
  private val venv = PyInterpreterRef.native(FusId("uv"), PyEnvRef(".venv"))

  @Test
  fun `the name of the root Python project holds the dot`() {
    assertEquals("Python: . native:uv:.venv", PyInterpreterLibraryName(PyInterpreterOwnerDir.ROOT, venv).toString())
  }

  @Test
  fun `a name parses back to its directory and ref`() {
    val name = PyInterpreterLibraryName(dir("libs/foo"), venv)
    assertEquals(name, PyInterpreterLibraryName.parse(name.toString()))
  }

  @Test
  fun `a ref with a Windows path keeps its colons`() {
    val ref = PyInterpreterRef.native(FusId("pip"), PyEnvRef("""C:\Python312\python.exe"""))
    val name = PyInterpreterLibraryName(PyInterpreterOwnerDir.ROOT, ref)
    assertEquals(ref, PyInterpreterLibraryName.parse(name.toString())?.ref)
  }

  @Test
  fun `a ref with spaces, brackets and a mode keeps them`() {
    val ref = PyInterpreterRef.native(FusId("uv"), PyEnvRef("/home/me/[x] native:y/@a %b/.venv"))
    val name = PyInterpreterLibraryName(dir("libs/foo"), ref)
    assertEquals(name, PyInterpreterLibraryName.parse(name.toString()))
  }

  @Test
  fun `a target ref parses`() {
    val ref = PyInterpreterRef(PyInterpreterRef.Mode.Target("3f2a"), FusId("conda"), PyEnvRef("ml"))
    val expected = PyInterpreterLibraryName(dir("app"), ref)
    assertEquals(expected, PyInterpreterLibraryName.parse("Python: app target:3f2a:conda:ml"))
  }

  @Test
  fun `the name of another library does not parse`() {
    assertNull(PyInterpreterLibraryName.parse("Maven: junit:junit:4.13"))
    assertNull(PyInterpreterLibraryName.parse("jupyter"))
    assertNull(PyInterpreterLibraryName.parse("Python:  native:uv:.venv"))
    assertNull(PyInterpreterLibraryName.parse("Python: .:native:uv:.venv"))
    // Without the prefix a name is the library of the user, even when the rest would parse.
    assertNull(PyInterpreterLibraryName.parse(". native:uv:.venv"))
  }

  @Test
  fun `a directory of the project is relative with slashes`() {
    val projectDir = Path.of("project")
    val root = PyInterpreterOwnerDir.ROOT
    assertEquals(root, PyInterpreterOwnerDir.of(projectDir, projectDir))
    assertEquals("libs/foo", PyInterpreterOwnerDir.of(projectDir, projectDir.resolve("libs").resolve("foo")).toString())
    assertEquals(projectDir.resolve("libs").resolve("foo"), dir("libs/foo").resolveIn(projectDir))
  }

  @Test
  fun `a directory outside the project starts with parent segments`() {
    val projectDir = Path.of("work", "project")
    val elsewhere = Path.of("work", "other", "app")
    val dir = PyInterpreterOwnerDir.of(projectDir, elsewhere)
    assertEquals("../other/app", dir.toString())
    assertEquals(elsewhere, dir.resolveIn(projectDir))
    assertEquals(dir, PyInterpreterOwnerDir.parse(dir.toString()))
  }

  @Test
  fun `a directory that no relative path reaches is absolute`() {
    val projectDir = Path.of("project").toAbsolutePath()
    val elsewhere = Path.of("elsewhere")
    val dir = PyInterpreterOwnerDir.of(projectDir, elsewhere)
    assertEquals("@elsewhere", dir.toString())
    assertEquals(elsewhere, dir.resolveIn(projectDir))
    assertEquals(dir, PyInterpreterOwnerDir.parse(dir.toString()))
  }

  @Test
  fun `a space, a mark and a percent in a directory are encoded`() {
    val projectDir = Path.of("project")
    val dir = PyInterpreterOwnerDir.of(projectDir, projectDir.resolve("my app").resolve("@v 100%"))
    assertEquals("my%20app/%40v%20100%25", dir.toString())
    assertEquals(projectDir.resolve("my app").resolve("@v 100%"), dir.resolveIn(projectDir))
    val name = PyInterpreterLibraryName(dir, venv)
    assertEquals(name, PyInterpreterLibraryName.parse(name.toString()))
  }

  @Test
  fun `a directory with a colon, a raw mark, a space, a bad escape or an inner dot segment does not parse`() {
    assertNull(PyInterpreterOwnerDir.parse("C:/app"))
    assertNull(PyInterpreterOwnerDir.parse("/abs"))
    assertNull(PyInterpreterOwnerDir.parse("app/../up"))
    assertNull(PyInterpreterOwnerDir.parse("./app"))
    assertNull(PyInterpreterOwnerDir.parse("app/@v"))
    assertNull(PyInterpreterOwnerDir.parse("my app"))
    assertNull(PyInterpreterOwnerDir.parse("app%2Fv"))
    assertNull(PyInterpreterOwnerDir.parse("@"))
    assertNull(PyInterpreterOwnerDir.parse("@D:/a@b"))
    assertEquals("../up", PyInterpreterOwnerDir.parse("../up").toString())
    assertEquals("..cache", PyInterpreterOwnerDir.parse("..cache").toString())
    assertEquals("@D:/lib", PyInterpreterOwnerDir.parse("@D:/lib").toString())
  }

  @Test
  fun `the interpreter entry is the project library with an interpreter name`() {
    val entry = LibraryDependency(LibraryId("Python: app native:uv:.venv", LibraryTableId.ProjectLibraryTableId), false, DependencyScope.COMPILE)
    val dependencies = listOf(
      ModuleSourceDependency,
      ModuleDependency(ModuleId("lib"), true, DependencyScope.COMPILE, false),
      LibraryDependency(LibraryId("jupyter", LibraryTableId.ProjectLibraryTableId), false, DependencyScope.COMPILE),
      entry,
    )
    assertEquals(entry, dependencies.pyInterpreterDependency)
  }

  @Test
  fun `a module library with an interpreter name is not the entry`() {
    val tableId = LibraryTableId.ModuleLibraryTableId(ModuleId("app"))
    val dependencies = listOf(LibraryDependency(LibraryId("Python: . native:uv:.venv", tableId), false, DependencyScope.COMPILE))
    assertNull(dependencies.pyInterpreterDependency)
  }

  @Test
  fun `setting the entry adds it last and keeps the other dependencies`() {
    val module = ModuleDependency(ModuleId("lib"), true, DependencyScope.COMPILE, false)
    val dependencies = mutableListOf<ModuleDependencyItem>(ModuleSourceDependency, module)
    val name = PyInterpreterLibraryName(PyInterpreterOwnerDir.ROOT, venv)
    dependencies.setPyInterpreterDependency(name)
    assertEquals(listOf(ModuleSourceDependency, module, entry(name)), dependencies)
  }

  @Test
  fun `setting another entry replaces it in its place`() {
    val old = PyInterpreterLibraryName(PyInterpreterOwnerDir.ROOT, venv)
    val new = PyInterpreterLibraryName(dir("app"), venv)
    val module = ModuleDependency(ModuleId("lib"), true, DependencyScope.COMPILE, false)
    val dependencies = mutableListOf(ModuleSourceDependency, entry(old), module)
    dependencies.setPyInterpreterDependency(new)
    assertEquals(listOf(ModuleSourceDependency, entry(new), module), dependencies)
  }

  @Test
  fun `setting no entry removes it`() {
    val dependencies = mutableListOf(ModuleSourceDependency, entry(PyInterpreterLibraryName(PyInterpreterOwnerDir.ROOT, venv)))
    dependencies.setPyInterpreterDependency(null)
    assertEquals(listOf<ModuleDependencyItem>(ModuleSourceDependency), dependencies)
  }

  private fun entry(name: PyInterpreterLibraryName): ModuleDependencyItem =
    LibraryDependency(LibraryId(name.toString(), LibraryTableId.ProjectLibraryTableId), false, DependencyScope.COMPILE)

  private fun dir(value: String): PyInterpreterOwnerDir = checkNotNull(PyInterpreterOwnerDir.parse(value))
}
