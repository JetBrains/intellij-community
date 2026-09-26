package com.intellij.ide.starter

import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityClient
import io.kotest.matchers.paths.shouldNotExist
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.writeText

/**
 * Off CI the starter stages nothing for publishing.
 *
 * `TeamCityClient.publishTeamCityArtifacts` copies the whole source tree into `teamcity-artifacts-for-publish`,
 * under a new suffixed directory on every call, and nothing deletes it. A launch publishes its logs, snapshots and
 * reports, so on a developer machine the copy grew without bound while the originals were already on disk.
 */
class TeamCityCIServerTest {
  @Test
  fun `off CI publishArtifact leaves the artifact where the launch wrote it`(@TempDir source: Path) {
    source.resolve("idea.log").writeText("log")
    val artifactPath = "off-ci-${UUID.randomUUID()}"
    val offCi = object : TeamCityCIServer(systemPropertiesFilePath = null) {
      override val isBuildRunningOnCI: Boolean = false
    }

    offCi.publishArtifact(source, artifactPath, "logs")

    TeamCityClient.artifactForPublishingDir.resolve(artifactPath).shouldNotExist()
  }
}
