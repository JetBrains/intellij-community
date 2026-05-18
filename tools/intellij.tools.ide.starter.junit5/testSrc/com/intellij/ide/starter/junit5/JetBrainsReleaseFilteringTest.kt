package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.community.JetBrainsDataServiceClient
import com.intellij.ide.starter.community.ProductInfoRequestParameters
import com.intellij.ide.starter.community.PublicIdeDownloader
import com.intellij.ide.starter.community.model.Download
import com.intellij.ide.starter.community.model.ReleaseInfo
import com.intellij.ide.starter.models.IdeInfoType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.random.Random

class JetBrainsReleaseFilteringTest {
  @Test
  fun `getting N latest public releases`() {
    val numberOfReleases = Random.nextInt(2, 10)
    val latestReleases = JetBrainsDataServiceClient.getLatestPublicReleases(productType = IdeInfoType.IDEA_ULTIMATE.productCode,
                                                                            numberOfReleases = numberOfReleases)
    latestReleases.shouldHaveSize(numberOfReleases)
    latestReleases.forEach { it.date.toString().shouldNotBeEmpty() }
    latestReleases.map { it.build }.toSet().shouldHaveSize(numberOfReleases)
  }

  @Test
  fun `filtering only major version of public releases`() {
    val numberOfReleases = Random.nextInt(2, 10)
    val latestReleases = JetBrainsDataServiceClient.getLatestPublicReleaseVersions(productType = IdeInfoType.IDEA_ULTIMATE.productCode,
                                                                                   numberOfReleases = numberOfReleases)
    latestReleases.shouldHaveSize(numberOfReleases)
    latestReleases.toSet().shouldHaveSize(numberOfReleases)
  }

  @Test
  fun `default selection picks latest major version even when an older major was published more recently`() {
    // Simulates a real situation: a 2024.3 patch was released after the latest 2025.1 build.
    // We expect the resolver to prefer the higher major (2025.1), matching LATEST-EAP-SNAPSHOT semantics.
    val olderMajorRecentlyPublished = release(date = "2026-03-15", majorVersion = "2024.3", version = "2024.3.6", build = "243.99999.1")
    val newerMajorPublishedEarlier = release(date = "2026-02-01", majorVersion = "2025.1", version = "2025.1", build = "251.10000.1")
    val newerMajorOldEap = release(date = "2025-12-10", majorVersion = "2025.1", version = "2025.1-EAP1", build = "251.5000.1", type = "eap")

    val releases = mapOf("IU" to listOf(olderMajorRecentlyPublished, newerMajorPublishedEarlier, newerMajorOldEap))

    val selected = PublicIdeDownloader().findSpecificRelease(releases, ProductInfoRequestParameters(type = "IU"))

    selected shouldBe newerMajorPublishedEarlier
  }

  private fun release(
    date: String,
    majorVersion: String,
    version: String,
    build: String,
    type: String = "release",
  ): ReleaseInfo = ReleaseInfo(
    date = LocalDate.parse(date),
    type = type,
    version = version,
    majorVersion = majorVersion,
    build = build,
    downloads = Download(null, null, null, null, null, null, null),
  )
}