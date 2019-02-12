/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.studio.updater

import com.android.testutils.TestUtils
import com.google.common.collect.MoreCollectors
import com.google.wireless.android.play.playlog.proto.ClientAnalytics
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.StudioPatchUpdaterEvent
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.rules.TempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.BufferedInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.ArrayList
import java.util.HashMap

/**
 * Integration test for the studio updater.
 *
 * This is formulated to check the packaging of the updater that may be changed by intellij merges,
 * hence uses the final binary, rather than being run as a unit test.
 */
@RunWith(JUnit4::class)
class StudioPatchUpdaterIntegrationTest {

  @get:Rule
  var myTempDirectory = TempDirectory()

  private lateinit var java: Path
  private lateinit var patchJar: Path

  enum class ExampleDirectory(private val files: Map<String, String>) {
    V1(mapOf("removed" to "v1_removed_later", "changed" to "v1_changed")),
    V2(mapOf("added" to "v2_added_since_v1", "changed" to "v2_changed")),
    V3(mapOf("changed" to "v3_changed"));

    internal fun createExampleDir(tempDirectory: TempDirectory): Path {
      val dir = tempDirectory.newFolder().toPath()
      for ((path, content) in files) {
        val file = dir.resolve(path)
        Files.createDirectories(file.parent)
        Files.write(file, setOf(content))
      }
      // Sanity test
      verifyDir(dir)
      return dir
    }

    internal fun verifyDir(dir: Path) {
      val actual = readDir(dir)
      assertEquals(files, actual)
    }

    private fun readDir(dir: Path): Map<String, String> {
      val actual = HashMap<String, String>()
      Files.walkFileTree(dir, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
          val filePath = dir.relativize(file).toString().replace(dir.fileSystem.separator, "/")
          actual[filePath] = Files.readAllLines(file).joinToString("\n")
          return FileVisitResult.CONTINUE
        }
      })
      return actual
    }

  }

  @Before
  fun createPatch() {
    java = Paths.get(System.getProperty("java.home")).resolve("bin").resolve(if (SystemInfo.isWindows) "java.exe" else "java")
    patchJar = myTempDirectory.newFolder("patch").toPath().resolve("patch.jar")

    // Build the patch

    val createPatcher = arrayOf(java.toString(), "-cp", updaterFullJar.toString(), UPDATER_MAIN_CLASS, "create", "v1", "v2",
                                ExampleDirectory.V1.createExampleDir(myTempDirectory).toString(),
                                ExampleDirectory.V2.createExampleDir(myTempDirectory).toString(), patchJar.toString(), "--strict")
    runExpectingOk(createPatcher, mapOf())
    assertTrue(Files.isRegularFile(patchJar))
  }

  /**
   * Smoke test for patch being correctly applied
   */
  @Test
  fun patchApplicationSmokeTest() {
    val analyticsHome = createAnalyticsHome()
    val env = mapOf("ANDROID_SDK_HOME" to analyticsHome.toString())

    // When V1 to V2 patch applied to V1
    val dir = ExampleDirectory.V1.createExampleDir(myTempDirectory)
    val applyPatch = arrayOf(java.toString(), "-cp", patchJar.toString(), UPDATER_MAIN_CLASS, "apply", dir.toString())
    // Patcher should succeed.
    runExpectingOk(applyPatch, env)

    // Result should be V2
    ExampleDirectory.V2.verifyDir(dir)

    // Check the events produced. This is kept as an integration test to also cover the packaging of the updater binary.
    val events = readEvents(analyticsHome)
    assertEquals(8, events.size.toLong())
    for (event in events) {
      assertEquals(AndroidStudioEvent.EventKind.STUDIO_PATCH_UPDATER, event.kind)
    }
    val expectedEventSequence = listOf(
      StudioPatchUpdaterEvent.Kind.START,
      StudioPatchUpdaterEvent.Kind.PHASE_EXTRACTING_PATCH_FILES,
      StudioPatchUpdaterEvent.Kind.PATCH_DETAILS_SHOW,
      StudioPatchUpdaterEvent.Kind.PHASE_VALIDATING_INSTALLATION,
      StudioPatchUpdaterEvent.Kind.PHASE_BACKING_UP_FILES,
      StudioPatchUpdaterEvent.Kind.PHASE_APPLYING_PATCH,
      StudioPatchUpdaterEvent.Kind.PHASE_CLEANING_UP,
      StudioPatchUpdaterEvent.Kind.EXIT_OK
    )
    assertEquals(expectedEventSequence, events.map { it.studioPatchUpdaterEvent.kind })

    val details = events.map { it.studioPatchUpdaterEvent }.find { it.kind == StudioPatchUpdaterEvent.Kind.PATCH_DETAILS_SHOW }!!
    assertEquals("v1", details.patch.studioVersionFrom)
    assertEquals("v2", details.patch.studioVersionTo)
  }

  /**
   * Smoke test for patch failing to be applied.
   */
  @Test
  fun patchApplicationFailureTest() {

    // When V1 to V2 patch applied to some other version
    val dir = ExampleDirectory.V3.createExampleDir(myTempDirectory)
    val applyPatch = arrayOf(java.toString(), "-cp", patchJar.toString(), UPDATER_MAIN_CLASS, "apply", dir.toString())
    // Patcher should fail
    runExpectingError(applyPatch, mapOf())

    // Version should not be corrupted.
    ExampleDirectory.V3.verifyDir(dir)
  }

  private fun readEvents(analyticsHome: Path): List<AndroidStudioEvent> {
    // Check the analytics were written.
    val spool = analyticsHome.resolve("metrics/spool")
    val trackFile: Path = Files.list(spool).use { paths -> paths.collect(MoreCollectors.onlyElement<Path>()) }
    val events = ArrayList<AndroidStudioEvent>()

    BufferedInputStream(Files.newInputStream(trackFile)).use { inputStream ->
      // read all LogEvents from the trackFile.
      while (true) {
        val event = ClientAnalytics.LogEvent.parseDelimitedFrom(inputStream) ?: break
        val studioEvent = AndroidStudioEvent.parseFrom(event.sourceExtension)
        events.add(studioEvent)
        println(studioEvent)
        println("---")
      }
    }
    return events
  }

  private fun createAnalyticsHome(): Path {
    val path = myTempDirectory.newFolder().toPath()
    val json = "{ userId: \"a4d47d92-8d4c-44bb-a8a4-d2483b6e0c16\", hasOptedIn: true }"
    Files.write(
      path.resolve("").resolve("analytics.settings"),
      json.toByteArray(StandardCharsets.UTF_8))
    return path
  }

  // See UpdateInstaller.UPDATER_MAIN_CLASS
  private val UPDATER_MAIN_CLASS = "com.intellij.updater.Runner"

  private fun runExpectingOk(command: Array<String>, env: Map<String, String>) {
    val returnValue = run(command, env)
    assertEquals("Expected command to run successfully " + command.joinToString(" "), 0, returnValue.toLong())
  }

  private fun runExpectingError(command: Array<String>, env: Map<String, String>) {
    val returnValue = run(command, env)
    assertNotEquals("Expected command to fail " + command.joinToString(" "), 0, returnValue.toLong())
  }

  private fun run(createPatcher: Array<String>, env: Map<String, String>): Int {
    val builder = ProcessBuilder(*createPatcher)
      .redirectOutput(ProcessBuilder.Redirect.INHERIT)
      .redirectError(ProcessBuilder.Redirect.INHERIT)
    builder.environment().putAll(env)
    return builder.start().waitFor()
  }

  private val updaterFullJar: Path
    get() {
      val root = TestUtils.getWorkspaceRoot().toPath()
      val bazelDeployJar = root.resolve("tools/idea/updater/updater_deploy.jar")
      if (Files.isRegularFile(bazelDeployJar)) {
        return bazelDeployJar
      }
      val antJar = root.resolve("tools/idea/out/studio/artifacts/updater-full.jar")
      if (Files.isRegularFile(antJar)) {
        return antJar
      }
      throw RuntimeException("Unable to find updater deploy jar. Perhaps run cd tools/idea && ant fullupdater")
    }

}