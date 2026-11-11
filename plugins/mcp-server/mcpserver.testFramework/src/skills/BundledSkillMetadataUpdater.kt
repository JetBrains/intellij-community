// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.testFramework.skills

import com.intellij.platform.testFramework.core.FileComparisonFailedError
import org.jetbrains.annotations.ApiStatus
import org.junit.jupiter.api.fail
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * How a skill's `metadata.json` compares to the content of its directory.
 *
 * Public for the test modules of the skill roots, which cannot see Kotlin `internal` across a module boundary.
 */
@ApiStatus.Internal
sealed interface BundledSkillMetadataStatus {
  /**
   * The recorded hash is the hash of the directory: nothing to record, nothing to bump.
   */
  object UpToDate : BundledSkillMetadataStatus

  /**
   * The file is missing or its hash is stale, and [expectedText] is what it should contain instead of [currentText] —
   * which is empty when the file does not exist yet, as is [recordedVersion].
   */
  class Outdated(
    val currentText: String,
    val expectedText: String,
    val recordedVersion: SkillVersion?,
    val expectedVersion: SkillVersion,
  ) : BundledSkillMetadataStatus

  /**
   * The file cannot be read, so there is no version to bump and no automatic fix. [reason] continues a sentence that
   * starts with the path of the file.
   */
  class Unreadable(val reason: String) : BundledSkillMetadataStatus
}

/**
 * Compares every bundled skill's `metadata.json` against the content of its directory, and — on request — writes the
 * corrected file. This is what turns a failure of a versioning test into a diff to review instead of a hash to copy by
 * hand.
 *
 * A stale skill gets a patch bump: the smallest bump that still tells the evaluation pipeline the skill changed.
 * Whether the change deserves a minor or a major bump instead is left to whoever reviews the diff, which is also why
 * nothing is written during a normal test run.
 */
@ApiStatus.Internal
object BundledSkillMetadataUpdater {
  /** System property that makes a versioning test write every stale or missing `metadata.json` before its checks run. */
  const val REGENERATE_PROPERTY: String = "bundled.skills.metadata.regenerate"

  private val FIRST_VERSION = SkillVersion(1, 0, 0)

  fun metadataFile(skillDirectory: Path): Path {
    return skillDirectory.resolve(BundledSkillFiles.METADATA_FILE_NAME)
  }

  /**
   * The text a versioning test appends to a stale-metadata failure: the command that writes the file. [testModule] and
   * [testClass] name the test to run. [extraNote] is added on its own line for a root with a rule of its own.
   */
  fun regenerateInstructions(testModule: String, testClass: KClass<*>, extraNote: String = ""): String {
    val instructions = """
      Have this written for you, then review the diff and commit:
        ./tests.cmd --module $testModule \
          --test ${testClass.qualifiedName} \
          -Dpass.$REGENERATE_PROPERTY=true
      The 'pass.' prefix is what forwards the property into the test JVM; from an IDE run configuration it is not needed.
      The version is bumped by a patch, so raise it to a minor or a major yourself when the skill's behavior changed for
      callers.
    """.trimIndent()
    return listOf(instructions, extraNote).filter(String::isNotEmpty).joinToString(separator = "\n")
  }

  /**
   * Writes every stale or missing `metadata.json` under [skillDirectories] when [REGENERATE_PROPERTY] is set, and
   * prints one line per file it wrote. Call it from `@BeforeAll`: the run that repairs the files is also the run that
   * confirms they are correct, and every check stays a pure check.
   */
  fun regenerateIfRequested(skillDirectories: List<Path>) {
    if (!System.getProperty(REGENERATE_PROPERTY).toBoolean()) return
    updateStaleMetadata(skillDirectories).forEach(::println)
  }

  /**
   * Fails when the `metadata.json` of [skillDirectory] is stale or missing. A stale file fails as a file comparison, so
   * the IDE shows the file on one side and the text it should hold on the other. [regenerateInstructions] tells the
   * reader how to have that text written.
   */
  fun assertUpToDate(skillDirectory: Path, regenerateInstructions: String) {
    val metadataFile = metadataFile(skillDirectory)

    when (val status = status(skillDirectory)) {
      BundledSkillMetadataStatus.UpToDate -> return
      is BundledSkillMetadataStatus.Unreadable -> fail(
        "${BundledSkillFiles.relativePath(metadataFile)} ${status.reason}\n" +
        "This is the one problem a regenerate run leaves alone: a file it cannot read has no version to bump either."
      )
      is BundledSkillMetadataStatus.Outdated -> throw FileComparisonFailedError(
        message = outdatedMetadataProblem(skillDirectory, status, regenerateInstructions),
        expected = status.currentText,
        actual = status.expectedText,
        // Points the diff at the file to update. There is no such file yet when the metadata is missing entirely, and
        // this argument only accepts one that exists.
        expectedFilePath = metadataFile.takeIf(Files::isRegularFile)?.toString(),
      )
    }
  }

  private fun outdatedMetadataProblem(
    skillDirectory: Path,
    status: BundledSkillMetadataStatus.Outdated,
    regenerateInstructions: String,
  ): String {
    val skillId = BundledSkillFiles.skillId(skillDirectory)
    val whatIsWrong = when (status.recordedVersion) {
      null -> "Skill '$skillId' has no ${BundledSkillFiles.METADATA_FILE_NAME}."
      else -> "Skill '$skillId' changed, but its version is still ${status.recordedVersion}."
    }
    return """
      $whatIsWrong
      Bundled skills are versioned so that the evaluation pipeline can tell when a skill changed: the recorded hash
      covers everything under ${BundledSkillFiles.relativePath(skillDirectory)}, and the version moves with it.
      The right side of the comparison is that file at version ${status.expectedVersion}.
    """.trimIndent() + "\n" + regenerateInstructions
  }

  fun status(skillDirectory: Path): BundledSkillMetadataStatus {
    val metadataFile = metadataFile(skillDirectory)
    val hash = BundledSkillDirectoryHash.compute(skillDirectory)

    if (!Files.isRegularFile(metadataFile)) {
      return outdated(currentText = "", recordedVersion = null, expectedVersion = FIRST_VERSION, hash = hash)
    }

    val metadata = runCatching { BundledSkillMetadataFormat.readMetadata(metadataFile) }
      .getOrElse { failure -> return BundledSkillMetadataStatus.Unreadable(failure.message.orEmpty()) }
    if (metadata.hash == hash) return BundledSkillMetadataStatus.UpToDate

    return outdated(
      currentText = Files.readString(metadataFile),
      recordedVersion = metadata.version,
      expectedVersion = metadata.version.nextPatch(),
      hash = hash,
    )
  }

  /**
   * Records the correct hash for every skill whose `metadata.json` is missing or stale, bumping the version along with
   * it, and returns one line per skill that was not left as it was, in the given order.
   */
  fun updateStaleMetadata(skillDirectories: List<Path>): List<String> =
    skillDirectories.mapNotNull(::updateSkill)

  private fun updateSkill(skillDirectory: Path): String? {
    val metadataFile = metadataFile(skillDirectory)
    val skill = "${BundledSkillFiles.skillId(skillDirectory)}: ${BundledSkillFiles.relativePath(metadataFile)}"

    return when (val status = status(skillDirectory)) {
      BundledSkillMetadataStatus.UpToDate -> null
      // The recorded version cannot be read, so it cannot be bumped either, and starting over from 1.0.0 would tell
      // the evaluation pipeline that the skill went backwards. Leave the file to a human and let the test report it.
      is BundledSkillMetadataStatus.Unreadable -> "$skill left as it is, it ${status.reason}"
      is BundledSkillMetadataStatus.Outdated -> {
        Files.writeString(metadataFile, status.expectedText)
        when (status.recordedVersion) {
          null -> "$skill created at ${status.expectedVersion}"
          else -> "$skill ${status.recordedVersion} -> ${status.expectedVersion}"
        }
      }
    }
  }

  private fun outdated(
    currentText: String,
    recordedVersion: SkillVersion?,
    expectedVersion: SkillVersion,
    hash: String,
  ): BundledSkillMetadataStatus.Outdated {
    return BundledSkillMetadataStatus.Outdated(
      currentText = currentText,
      expectedText = BundledSkillMetadataFormat.metadataText(BundledSkillMetadata(expectedVersion, hash)),
      recordedVersion = recordedVersion,
      expectedVersion = expectedVersion,
    )
  }
}
