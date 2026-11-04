// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Properties
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

class MavenWorkspaceMapWrapperTest {

  /**
   * IDEA-388560: when a workspace artifact's version is the unresolved literal `${revision}`
   * (because the property lives in pom `<properties>` rather than `-Drevision=...`),
   * the existing exact-key lookup misses for concrete-version requests like `5.0.1-SNAPSHOT`.
   * The fallback must accept the literal entry iff the pom's effective `<revision>` matches.
   */
  @Test
  fun `falls back to placeholder entry when its resolved revision matches concrete request`() {
    val tmp = createTempDirectory()
    val pom = tmp.resolve("pom.xml")
    pom.writeText(
      """
      <project>
        <groupId>com.example</groupId>
        <artifactId>module</artifactId>
        <version>${"$"}{revision}</version>
        <properties>
          <revision>5.0.1-SNAPSHOT</revision>
        </properties>
      </project>
      """.trimIndent()
    )

    val rawMap = MavenWorkspaceMap()
    rawMap.register(MavenId("com.example", "module", "\${revision}"), pom.toFile())
    val wrapper = MavenWorkspaceMapWrapper(rawMap, Properties())

    val data = wrapper.findFileAndOriginalIdWithRevisionFallback(
      MavenId("com.example", "module", "5.0.1-SNAPSHOT")
    )

    assertThat(data).isNotNull
    assertThat(data!!.getFile(MavenConstants.POM_EXTENSION)).isEqualTo(pom.toFile())
  }

  /**
   * The fallback must NOT short-circuit when the requested concrete version differs from
   * what the placeholder would resolve to (e.g. a sibling module declares parent at a
   * frozen dated release like `2026.05.22T09.56` while the workspace runs at
   * `${revision}=5.0.1-SNAPSHOT`). Returning the workspace data here would bind the
   * dependency to the wrong artifact version.
   */
  @Test
  fun `does not match when placeholder resolves to a different version`() {
    val tmp = createTempDirectory()
    val pom = tmp.resolve("pom.xml")
    pom.writeText(
      """
      <project>
        <groupId>com.example</groupId>
        <artifactId>module</artifactId>
        <version>${"$"}{revision}</version>
        <properties>
          <revision>5.0.1-SNAPSHOT</revision>
        </properties>
      </project>
      """.trimIndent()
    )

    val rawMap = MavenWorkspaceMap()
    rawMap.register(MavenId("com.example", "module", "\${revision}"), pom.toFile())
    val wrapper = MavenWorkspaceMapWrapper(rawMap, Properties())

    val data = wrapper.findFileAndOriginalIdWithRevisionFallback(
      MavenId("com.example", "module", "2026.05.22T09.56")
    )

    assertThat(data).isNull()
  }

  /**
   * Exact-version lookups must still work unchanged.
   */
  @Test
  fun `exact match returns directly without fallback`() {
    val tmp = createTempDirectory()
    val pom = tmp.resolve("pom.xml")
    pom.writeText("<project/>")

    val rawMap = MavenWorkspaceMap()
    rawMap.register(MavenId("com.example", "module", "5.0.1-SNAPSHOT"), pom.toFile())
    val wrapper = MavenWorkspaceMapWrapper(rawMap, Properties())

    val data = wrapper.findFileAndOriginalIdWithRevisionFallback(
      MavenId("com.example", "module", "5.0.1-SNAPSHOT")
    )

    assertThat(data).isNotNull
  }

  /**
   * Fallback walks the `<parent><relativePath>` chain so the placeholder can be defined
   * in an aggregator pom (typical for boot-parent style hierarchies).
   */
  @Test
  fun `placeholder resolves via parent pom relative path`() {
    val tmp = createTempDirectory()
    val parent = tmp.resolve("pom.xml")
    parent.writeText(
      """
      <project>
        <groupId>com.example</groupId>
        <artifactId>parent</artifactId>
        <version>${"$"}{revision}</version>
        <properties>
          <revision>5.0.1-SNAPSHOT</revision>
        </properties>
      </project>
      """.trimIndent()
    )
    val moduleDir = tmp.resolve("child").also { java.nio.file.Files.createDirectories(it) }
    val childPom = moduleDir.resolve("pom.xml")
    childPom.writeText(
      """
      <project>
        <parent>
          <groupId>com.example</groupId>
          <artifactId>parent</artifactId>
          <version>${"$"}{revision}</version>
          <relativePath>../pom.xml</relativePath>
        </parent>
        <artifactId>child</artifactId>
      </project>
      """.trimIndent()
    )

    val rawMap = MavenWorkspaceMap()
    rawMap.register(MavenId("com.example", "child", "\${revision}"), childPom.toFile())
    val wrapper = MavenWorkspaceMapWrapper(rawMap, Properties())

    val data = wrapper.findFileAndOriginalIdWithRevisionFallback(
      MavenId("com.example", "child", "5.0.1-SNAPSHOT")
    )

    assertThat(data).isNotNull
  }
}
