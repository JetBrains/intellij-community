// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.testFramework.skills

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Content hash of a bundled skill directory, recorded as `hash` in the skill's `metadata.json`.
 *
 * The hash covers every regular file below the skill directory, so `SKILL.md`, referenced instructions, reference
 * docs and binary assets all affect it. The only exclusion is the skill root's own `metadata.json`, because it stores
 * the resulting hash.
 *
 * The digest input is stable across platforms: relative paths always use `/`, entries are sorted by relative path,
 * and file bytes are hashed as committed (the repository checks out `LF` everywhere, see the root `.gitattributes`).
 */
internal object BundledSkillDirectoryHash {
  const val HASH_PREFIX: String = "sha256:"

  val HASH_FORMAT: Regex = Regex("^${HASH_PREFIX}[0-9a-f]{64}$")

  fun compute(skillDirectory: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    for (relativePath in hashedRelativePaths(skillDirectory)) {
      digest.update(relativePath.toByteArray(Charsets.UTF_8))
      digest.update(0)
      digest.update(fileDigest(skillDirectory.resolve(relativePath)))
    }
    return HASH_PREFIX + digest.digest().toHexString()
  }

  private fun hashedRelativePaths(skillDirectory: Path): List<String> {
    Files.walk(skillDirectory).use { paths ->
      return paths
        .filter(Files::isRegularFile)
        .map(skillDirectory::relativize)
        .filter { relativePath -> !isExcluded(relativePath) }
        .map { relativePath -> relativePath.joinToString("/") }
        .sorted()
        .toList()
    }
  }

  private fun isExcluded(relativePath: Path): Boolean {
    return relativePath.nameCount == 1
           && relativePath.fileName.toString() == BundledSkillFiles.METADATA_FILE_NAME
  }

  private fun fileDigest(path: Path): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
  }

  private fun ByteArray.toHexString(): String = joinToString("") { byte -> "%02x".format(byte) }
}
