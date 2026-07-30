// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.requirements.PyRequirementEnvMarkerAndSet
import com.intellij.python.requirements.PyRequirementEnvMarkerImpl
import com.intellij.python.requirements.PyRequirementEnvMarkerOrSet
import com.intellij.python.requirements.PyRequirementEnvMarkerRelation
import com.intellij.python.requirements.parser.PyRequirementParser
import com.jetbrains.python.packaging.requirement.PyRequirementEnvMarkerType
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.writeText

@PyDefaultTestApplication
@TestClassInfo(repository = Repository.PY_PROFESSIONAL)
class PyRequirementsParserTest(val project: Project) {
  @ParameterizedTest
  @ValueSource(strings = ["mypackage >= 1.23.4a2, < 2.0", "mypackage>=1.23.4a2,<2.0"])
  fun testVersionedRequirement(text: String) {
    val req = PyRequirementParser.fromLine(text, project)
    assertNotNull(req)
    assertEquals("mypackage", req.name)
    assertEquals("", req.extras)
    assertEquals(2, req.versionSpecs.size)
    assertEquals("1.23.4a2", req.versionSpecs[0].version)
    assertEquals(PyRequirementRelation.GTE, req.versionSpecs[0].relation)
    assertEquals("2.0", req.versionSpecs[1].version)
    assertEquals(PyRequirementRelation.LT, req.versionSpecs[1].relation)
    assertEquals(listOf("mypackage"), req.installOptions)
  }

  @ParameterizedTest
  @ValueSource(strings = ["mypackage [ extra1, extra2 ] >= 1.23.4a2, < 2.0", "mypackage[extra1,extra2]>=1.23.4a2,<2.0"])
  fun testComplexRequirement(basePart: String) {
    val text = "${basePart} --hash=sha256:2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824" +
               " ; python_version < '3.14' and platform_system != 'Windows'"
    val req = PyRequirementParser.fromLine(text, project)
    assertNotNull(req)
    assertEquals("mypackage", req.name)
    assertEquals("extra1,extra2", req.extras)
    assertEquals(2, req.versionSpecs.size)
    assertEquals("1.23.4a2", req.versionSpecs[0].version)
    assertEquals(PyRequirementRelation.GTE, req.versionSpecs[0].relation)
    assertEquals("2.0", req.versionSpecs[1].version)
    assertEquals(PyRequirementRelation.LT, req.versionSpecs[1].relation)

    assertEquals(2, req.installOptions.size)
    assertEquals("mypackage[extra1,extra2]", req.installOptions[0])
    assertEquals("--hash=sha256:2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", req.installOptions[1])

    val envMarker = req.environmentMarker
    assertInstanceOf<PyRequirementEnvMarkerAndSet>(envMarker)
    assertEquals(2, envMarker.markers.size)

    val firstMarker = envMarker.markers[0]
    assertInstanceOf<PyRequirementEnvMarkerImpl>(firstMarker)
    assertEquals(PyRequirementEnvMarkerType.PYTHON_VERSION, firstMarker.type)
    assertEquals(PyRequirementEnvMarkerRelation.LT, firstMarker.relation)
    assertEquals(1, firstMarker.values.size)
    assertEquals("3.14", firstMarker.values[0])

    val secondMarker = envMarker.markers[1]
    assertInstanceOf<PyRequirementEnvMarkerImpl>(secondMarker)
    assertNotNull(secondMarker)
    assertEquals(PyRequirementEnvMarkerType.PLATFORM_SYSTEM, secondMarker.type)
    assertEquals(PyRequirementEnvMarkerRelation.NE, secondMarker.relation)
    assertEquals(1, secondMarker.values.size)
    assertEquals("Windows", secondMarker.values[0])

    val platformData = mapOf(
      PyRequirementEnvMarkerType.PLATFORM_SYSTEM to "Linux",
      PyRequirementEnvMarkerType.PYTHON_VERSION to "3.13"
    )
    assertEquals(true, envMarker.matches(platformData))
  }

  @Test
  fun testIndividualOptions() {
    val text = "mypackage " +
               "--hash=SHA256:2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824 " +
               "--config-settings=foo=bar " +
               "--config-settings xyz=abc " +
               "--global-option=\"build_ext\" " +
               "--global-option \"build_ext\" " +
               "--hash sha256:09a6bb58a2f46ac7c9fd8ca824ff20ce19899b6009be888a8952de2235f2612c"
    val req = PyRequirementParser.fromLine(text, project)
    assertNotNull(req)
    assertEquals(7, req.installOptions.size)
    assertEquals("mypackage", req.installOptions[0])
    assertEquals("--hash=SHA256:2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", req.installOptions[1])
    assertEquals("--config-settings=foo=bar", req.installOptions[2])
    assertEquals("--config-settings xyz=abc", req.installOptions[3])
    assertEquals("--global-option=\"build_ext\"", req.installOptions[4])
    assertEquals("--global-option \"build_ext\"", req.installOptions[5])
    assertEquals("--hash sha256:09a6bb58a2f46ac7c9fd8ca824ff20ce19899b6009be888a8952de2235f2612c", req.installOptions[6])
  }

  @Test
  fun testComplexEnvironmentMarker() {
    val text = "mypackage; (python_version < '3.14' and platform_system != 'Windows') or " +
               "(platform_python_implementation == 'PyPy' and python_version >= '7.3.8')"
    val req = PyRequirementParser.fromLine(text, project)
    assertNotNull(req)

    assertEquals("mypackage", req.name)
    assertEquals("", req.extras)
    assertEquals(0, req.versionSpecs.size)
    assertEquals(listOf("mypackage"), req.installOptions)

    val envMarker = req.environmentMarker
    assertInstanceOf<PyRequirementEnvMarkerOrSet>(envMarker)
    assertEquals(2, envMarker.markers.size)

    var andSet = envMarker.markers[0]
    assertInstanceOf<PyRequirementEnvMarkerAndSet>(andSet)
    assertEquals(2, andSet.markers.size)
    var firstMarker = assertInstanceOf<PyRequirementEnvMarkerImpl>(andSet.markers[0])
    assertEquals(PyRequirementEnvMarkerType.PYTHON_VERSION, firstMarker.type)
    assertEquals(PyRequirementEnvMarkerRelation.LT, firstMarker.relation)
    assertEquals(1, firstMarker.values.size)
    assertEquals("3.14", firstMarker.values[0])
    var secondMarker = assertInstanceOf<PyRequirementEnvMarkerImpl>(andSet.markers[1])
    assertEquals(PyRequirementEnvMarkerType.PLATFORM_SYSTEM, secondMarker.type)
    assertEquals(1, secondMarker.values.size)
    assertEquals(PyRequirementEnvMarkerRelation.NE, secondMarker.relation)
    assertEquals("Windows", secondMarker.values[0])

    andSet = envMarker.markers[1]
    assertInstanceOf<PyRequirementEnvMarkerAndSet>(andSet)
    assertEquals(2, andSet.markers.size)
    firstMarker = assertInstanceOf<PyRequirementEnvMarkerImpl>(andSet.markers[0])
    assertEquals(PyRequirementEnvMarkerType.PLATFORM_PYTHON_IMPLEMENTATION, firstMarker.type)
    assertEquals(PyRequirementEnvMarkerRelation.EQ, firstMarker.relation)
    assertEquals(1, firstMarker.values.size)
    assertEquals("PyPy", firstMarker.values[0])
    secondMarker = assertInstanceOf<PyRequirementEnvMarkerImpl>(andSet.markers[1])
    assertEquals(PyRequirementEnvMarkerType.PYTHON_VERSION, secondMarker.type)
    assertEquals(PyRequirementEnvMarkerRelation.GTE, secondMarker.relation)
    assertEquals(1, secondMarker.values.size)
    assertEquals("7.3.8", secondMarker.values[0])

    val platformData = mapOf(
      PyRequirementEnvMarkerType.PLATFORM_SYSTEM to "Linux",
      PyRequirementEnvMarkerType.PYTHON_VERSION to "3.13"
    )
    assertEquals(true, envMarker.matches(platformData))
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "urllib3 [ extra1, extra2 ] @ https://github.com/urllib3/urllib3/archive/refs/tags/1.26.8.zip ; " +
    "python_version < \"3.14\" and platform_system != \"Windows\"",
    "urllib3[extra1,extra2]@https://github.com/urllib3/urllib3/archive/refs/tags/1.26.8.zip;" +
    "python_version<\"3.14\" and platform_system!=\"Windows\"",
  ])
  fun testUrlRequirement(text: String) {
    val req = PyRequirementParser.fromLine(text, project)
    assertNotNull(req)
    assertEquals("extra1,extra2", req.extras)
    assertEquals(0, req.versionSpecs.size)
    assertEquals("urllib3", req.name)
    assertEquals("https://github.com/urllib3/urllib3/archive/refs/tags/1.26.8.zip", req.urlReference)

    val envMarker = req.environmentMarker
    assertInstanceOf<PyRequirementEnvMarkerAndSet>(envMarker)
    assertEquals(2, envMarker.markers.size)
  }


  @ParameterizedTest
  @ValueSource(strings = [
    "git+https://example.org/project.git",
    "git+user@example.org:project.git",
    "bzr+lp:project",
  ])
  fun testVCSRequirement(uri: String) {
    val text = "example-project @ $uri"
    val req = PyRequirementParser.fromLine(text, project)
    assertNotNull(req)
    assertEquals(0, req.versionSpecs.size)
    assertEquals("example-project", req.name)
    assertEquals(uri, req.urlReference)
  }

  @ParameterizedTest
  @ValueSource(strings = ["-e", "--editable"])
  fun testEditableRequirement(option: String) {
    val text = "${option} mypackage"
    val req = PyRequirementParser.fromLine(text, project)
    assertNotNull(req)
    assertEquals("", req.extras)
    assertEquals(0, req.versionSpecs.size)
    assertEquals("mypackage", req.name)
    assertEquals(listOf(option, "mypackage"), req.installOptions)
  }

  @Test
  fun testMutuallyReferencingFilesDoNotOverflow(@TempDir dir: Path) {
    (dir / "a.txt").writeText("-r b.txt\npkg-a==1.0")
    (dir / "b.txt").writeText("-r a.txt\npkg-b==1.0")
    val aVf = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(dir / "a.txt")!!

    // fromFile acquires its own read action; a StackOverflowError here would mean the cycle guard is broken.
    val names = PyRequirementParser.fromFile(aVf, project).map { it.name }.toSet()
    assertEquals(setOf("pkg-a", "pkg-b"), names)
  }

  @Test
  fun testSelfReferencingFileDoesNotOverflow(@TempDir dir: Path) {
    (dir / "self.txt").writeText("-r self.txt\npkg-c==1.0")
    val selfVf = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(dir / "self.txt")!!

    val names = PyRequirementParser.fromFile(selfVf, project).map { it.name }.toSet()
    assertEquals(setOf("pkg-c"), names)
  }
}