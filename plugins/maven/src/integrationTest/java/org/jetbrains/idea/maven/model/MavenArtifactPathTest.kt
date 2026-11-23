// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The library root paths of [MavenArtifact.getPathForExtraArtifact].
 *
 * A snapshot from a remote repository gets the file name of its timestamped version (IDEA-73118), also when Maven
 * resolved the base-version copy. The timestamped file does not change, but Maven replaces the base-version copy with
 * each new snapshot. With the copy as the library root, Maven 4 on Windows did not update the snapshot (IDEA-394629).
 */
class MavenArtifactPathTest {

  private val repository = File("/repo")
  private val dir = "/repo/test/foo/1-SNAPSHOT"

  private fun artifact(version: String, baseVersion: String, file: File?) =
    MavenArtifact("test", "foo", version, baseVersion, "jar", null, "compile", false, "jar", file, repository, true, false)

  @Test
  fun `a remote snapshot gets the timestamped file name, not the base-version copy`() {
    // Maven resolves a timestamped snapshot to its base-version copy (snapshot normalization of Maven Resolver).
    val artifact = artifact("1-20261123.123735-1", "1-SNAPSHOT", File("$dir/foo-1-SNAPSHOT.jar"))

    assertEquals("$dir/foo-1-20261123.123735-1.jar", artifact.getPathForExtraArtifact(null, null))
    assertEquals("$dir/foo-1-20261123.123735-1-sources.jar", artifact.getPathForExtraArtifact("sources", null))
    assertEquals("$dir/foo-1-20261123.123735-1-javadoc.jar", artifact.getPathForExtraArtifact("javadoc", null))
  }

  @Test
  fun `a local snapshot and a release keep the resolved file`() {
    val snapshot = artifact("1-SNAPSHOT", "1-SNAPSHOT", File("$dir/foo-1-SNAPSHOT.jar"))
    assertEquals("$dir/foo-1-SNAPSHOT.jar", snapshot.getPathForExtraArtifact(null, null))
    assertEquals("$dir/foo-1-SNAPSHOT-sources.jar", snapshot.getPathForExtraArtifact("sources", null))

    val release = artifact("1.5", "1.5", File("/repo/test/foo/1.5/foo-1.5.jar"))
    assertEquals("/repo/test/foo/1.5/foo-1.5.jar", release.getPathForExtraArtifact(null, null))
    assertEquals("/repo/test/foo/1.5/foo-1.5-javadoc.jar", release.getPathForExtraArtifact("javadoc", null))
  }
}
