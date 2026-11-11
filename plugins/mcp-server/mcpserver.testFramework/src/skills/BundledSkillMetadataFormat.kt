// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.testFramework.skills

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Version of a bundled skill, `major.minor.patch`.
 */
@ApiStatus.Internal
data class SkillVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SkillVersion> {
  override fun compareTo(other: SkillVersion): Int {
    return compareValuesBy(this, other, SkillVersion::major, SkillVersion::minor, SkillVersion::patch)
  }

  override fun toString(): String = "$major.$minor.$patch"

  fun nextPatch(): SkillVersion = copy(patch = patch + 1)

  companion object {
    private val FORMAT = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")

    fun parseOrNull(value: String): SkillVersion? {
      val match = FORMAT.matchEntire(value) ?: return null
      val (major, minor, patch) = match.destructured
      return SkillVersion(major.toInt(), minor.toInt(), patch.toInt())
    }
  }
}

/**
 * Contents of a skill's `metadata.json`.
 */
internal data class BundledSkillMetadata(val version: SkillVersion, val hash: String)

/**
 * Reads a skill's `metadata.json`.
 *
 * Every rejection is reported as an [IllegalArgumentException] whose message names the offending field and reads as the
 * continuation of a sentence starting with the file path, so the validation test can print it after that path as is.
 */
internal object BundledSkillMetadataFormat {
  private const val VERSION_FIELD = "version"
  private const val HASH_FIELD = "hash"

  private val METADATA_FIELDS = setOf(VERSION_FIELD, HASH_FIELD)

  fun readMetadata(file: Path): BundledSkillMetadata {
    val text = try {
      Files.readString(file)
    }
    catch (e: IOException) {
      throw IllegalArgumentException("cannot be read: ${e.message}")
    }

    val metadata = parseObject(text)
    val unexpectedFields = metadata.keys - METADATA_FIELDS
    require(unexpectedFields.isEmpty()) {
      "has unexpected field(s) ${unexpectedFields.sorted()}; only ${METADATA_FIELDS.sorted()} are allowed"
    }

    return BundledSkillMetadata(
      version = version(metadata),
      hash = hash(metadata),
    )
  }

  /**
   * The full text of a skill's `metadata.json` in the committed format: two-space indent, `version` before `hash`, and
   * a trailing newline.
   *
   * The text is built directly rather than through a JSON encoder because both values are already constrained —
   * [SkillVersion] is digits and dots, and a hash matches [BundledSkillDirectoryHash.HASH_FORMAT] — so there is
   * nothing to escape, and the committed formatting is reproduced byte for byte.
   *
   * This is also what a failing check diffs the file against, so the text under review and the text that gets written
   * are the same text.
   */
  fun metadataText(metadata: BundledSkillMetadata): String {
    return """
      {
        "$VERSION_FIELD": "${metadata.version}",
        "$HASH_FIELD": "${metadata.hash}"
      }
    """.trimIndent() + "\n"
  }

  private fun parseObject(text: String): JsonObject {
    val root = try {
      Json.parseToJsonElement(text)
    }
    catch (e: SerializationException) {
      throw IllegalArgumentException("is not valid JSON: ${e.message}")
    }
    return root as? JsonObject ?: throw IllegalArgumentException("must contain a JSON object")
  }

  private fun version(metadata: JsonObject): SkillVersion {
    val rawVersion = string(metadata, VERSION_FIELD)
    return SkillVersion.parseOrNull(rawVersion)
           ?: throw IllegalArgumentException(
             "'$VERSION_FIELD' is '$rawVersion' but must be 'major.minor.patch', for example '1.0.0'"
           )
  }

  private fun hash(metadata: JsonObject): String {
    val rawHash = string(metadata, HASH_FIELD)
    require(BundledSkillDirectoryHash.HASH_FORMAT.matches(rawHash)) {
      "'$HASH_FIELD' is '$rawHash' but must be '${BundledSkillDirectoryHash.HASH_PREFIX}' followed by 64 lowercase hex characters"
    }
    return rawHash
  }

  private fun string(metadata: JsonObject, field: String): String {
    val value = metadata[field] ?: throw IllegalArgumentException("has no '$field' field")
    val primitive = value as? JsonPrimitive
    require(primitive != null && primitive.isString) { "'$field' must be a string" }
    return primitive.content
  }
}
