// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.platform.bazel.runfiles.BazelLabel
import junit.framework.ComparisonFailure
import org.jetbrains.kotlin.idea.artifacts.KotlinJpsBuilderTestArtifacts
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

/**
 * The test data of the Kotlin JPS builder tests.
 *
 * The Kotlin repository publishes its `jps/jps-plugin/testData` directory, see [KotlinJpsBuilderTestArtifacts.testDataDir].
 * The overlay directory of this module has the same paths. An overlay file replaces the artifact file at the same path.
 * An overlay case that the artifact does not have is a new case. The golden files that the tests overwrite go to the
 * overlay, never to the artifact.
 */
object KotlinJpsTestData {
  private const val OVERLAY_PATH = "plugins/kotlin/jps/builder/tests/testData"
  private const val OVERLAY_LABEL = "@community//plugins/kotlin/jps/builder/tests:testData"

  /** The file of a case that mutes it, with lines of the form `<version prefix or *>: <reason>`. */
  const val MUTED_FILE_NAME: String = "ij-muted.txt"

  val artifactRoot: Path
    get() = KotlinJpsBuilderTestArtifacts.testDataDir

  /** The overlay in the source tree. */
  val overlaySourceRoot: Path by lazy { Path.of(PathManager.getCommunityHomePath(), OVERLAY_PATH) }

  /** The overlay to read. Under Bazel, it comes from the runfiles. */
  private val overlayRoot: Path? by lazy {
    if (BazelTestUtil.isUnderBazelTest) {
      runCatching { BazelTestUtil.getFileFromBazelRuntime(BazelLabel.fromString(OVERLAY_LABEL)) }.getOrNull()?.takeIf { it.isDirectory() }
    }
    else {
      overlaySourceRoot.takeIf { it.isDirectory() }
    }
  }

  /** The roots in the order of precedence: the overlay wins. */
  val roots: List<Path>
    get() = listOfNotNull(artifactRoot, overlayRoot)

  /** Copies the directory [relativePath] into [target], first from the artifact, then from the overlay. */
  fun materialize(relativePath: String, target: Path) {
    var found = false
    for (root in roots) {
      val source = root.resolve(relativePath)
      if (!source.isDirectory()) continue
      found = true
      Files.walk(source).use { paths ->
        for (path in paths) {
          val destination = target.resolve(path.relativeTo(source).toString())
          if (path.isDirectory()) {
            destination.createDirectories()
          }
          else {
            Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
          }
        }
      }
    }
    check(found) { "No test data '$relativePath' in ${roots.joinToString()}" }
  }

  /** @return the reason when [caseDir] mutes the case for [compilerVersion], otherwise `null`. */
  fun mutedReason(caseDir: Path, compilerVersion: String): String? {
    val file = caseDir.resolve(MUTED_FILE_NAME)
    if (!file.isRegularFile()) return null
    for (line in file.readLines()) {
      val prefix = line.substringBefore(':').trim()
      if (prefix.isEmpty()) continue
      if (prefix == "*" || compilerVersion.startsWith(prefix)) return line.substringAfter(':').trim()
    }
    return null
  }

  /**
   * The golden file names for [compilerVersion], most specific first: `<base>.bta-<full version>.<extension>`, then
   * `<base>.bta-<major.minor>.<extension>`, then [fallbackNames].
   */
  fun goldenNames(versionedName: String, compilerVersion: String, fallbackNames: List<String>): List<String> {
    val base = versionedName.substringBeforeLast('.')
    val extension = versionedName.substringAfterLast('.')
    val majorMinor = compilerVersion.split('.').take(2).joinToString(".")
    return listOf("$base.bta-$compilerVersion.$extension", "$base.bta-$majorMinor.$extension") + fallbackNames
  }

  /**
   * Compares [actual] with the first file of [names] that the materialized [caseDir] has.
   *
   * With `idea.tests.overwrite.data`, a difference goes to the overlay of [relativePath]. A match of a version-specific
   * name keeps its name. Any other match becomes [overwriteName].
   */
  fun assertGolden(
    relativePath: String,
    caseDir: Path,
    names: List<String>,
    overwriteName: String,
    actual: String,
    normalize: (String) -> String = { it },
  ) {
    val foundName = names.firstOrNull { caseDir.resolve(it).isRegularFile() }
    val expected = foundName?.let { StringUtil.convertLineSeparators(caseDir.resolve(it).readText()) }
    val actualText = StringUtil.convertLineSeparators(actual)
    if (expected != null && normalize(expected).trimEnd() == normalize(actualText).trimEnd()) return

    val targetName = if (foundName != null && ".bta-" in foundName) foundName else overwriteName
    val target = overlaySourceRoot.resolve(relativePath).resolve(targetName)
    if (UsefulTestCase.OVERWRITE_TESTDATA) {
      target.parent.createDirectories()
      target.writeText(actualText.trimEnd() + "\n")
      println("File $target created.")
      return
    }
    val expectedPath = foundName?.let { name -> roots.reversed().map { it.resolve(relativePath).resolve(name) }.firstOrNull { it.isRegularFile() } }
    throw ComparisonFailure(
      "Golden file of $relativePath: ${expectedPath ?: "none of $names"}. Run with idea.tests.overwrite.data=true to write $target",
      expected?.trimEnd() ?: "",
      actualText.trimEnd(),
    )
  }
}
