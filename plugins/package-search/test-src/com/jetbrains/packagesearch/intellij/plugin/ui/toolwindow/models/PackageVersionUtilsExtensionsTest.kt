package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.PackageSearchTestUtils
import com.jetbrains.packagesearch.packageversionutils.PackageVersionUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class PackageVersionUtilsExtensionsTest {

    private val allVersionsList = loadFromRes("versions_list/versions-with-edge-cases.csv")
    private val stableVersionsList = allVersionsList.filter { it.isStable }

    @Nested
    inner class UpdateCandidateVersion {

        @Test
        internal fun `should return the highest sensible version by name when the current version is Missing`() {
            val actual = PackageVersionUtils.upgradeCandidateVersionOrNull(PackageVersion.Missing, allVersionsList)
            val expected = PackageVersion.Named(versionName = "3.1-alpha01", isStable = false, releasedAt = 1132682950000L)
            assertEquals(expected, actual)
        }

        @Test
        internal fun `should ignore old silly version names when the current version is Missing`() {
            val versionsWithOldSillyEntry = allVersionsList + PackageVersion.Named("20190315.1753", true, 1129356488000)
            val actual = PackageVersionUtils.upgradeCandidateVersionOrNull(PackageVersion.Missing, versionsWithOldSillyEntry)
            val expected = PackageVersion.Named(versionName = "3.1-alpha01", isStable = false, releasedAt = 1132682950000L)
            assertEquals(expected, actual)
        }

        @Test
        internal fun `should ignore new silly version names when the current version is Missing`() {
            val versionsWithNewSillyEntry = allVersionsList + PackageVersion.Named("20190315.1753", true, 1888888888000)
            val actual = PackageVersionUtils.upgradeCandidateVersionOrNull(PackageVersion.Missing, versionsWithNewSillyEntry)
            val expected = PackageVersion.Named(versionName = "3.1-alpha01", isStable = false, releasedAt = 1132682950000L)
            assertEquals(expected, actual)
        }

        @Test
        internal fun `should return null when the current version is higher than the highest one in the list`() {
            val newerVersion = PackageVersion.Named(versionName = "4.0.0", isStable = true, releasedAt = 2000000000000L)
            val actual = PackageVersionUtils.upgradeCandidateVersionOrNull(newerVersion, allVersionsList)
            assertNull(actual)
        }

        @Test
        internal fun `should return the highest sensible version when the current version is not semver-looking`() {
            val version = PackageVersion.Named(versionName = "banana", isStable = false, releasedAt = 1234567891234L)
            val actual = PackageVersionUtils.upgradeCandidateVersionOrNull(version, stableVersionsList)
            val expected = PackageVersion.Named(versionName = "2.11.0", isStable = true, releasedAt = 1625881379000L)
            assertEquals(expected, actual)
        }

        @Test
        internal fun `should return the highest sensible version when the current version is semver-looking and not the latest (stable only)`() {
            val currentVersion = PackageVersion.Named(versionName = "2.10.0", isStable = true, releasedAt = 1623329019000L)
            val actual = PackageVersionUtils.upgradeCandidateVersionOrNull(currentVersion, stableVersionsList)
            val expected = PackageVersion.Named(versionName = "2.11.0", isStable = true, releasedAt = 1625881379000L)
            assertEquals(expected, actual)
        }

        @Test
        internal fun `should return the highest sensible version when the current version is semver-looking and not the latest (unstable as well)`() {
            val currentVersion = PackageVersion.Named(versionName = "2.10.0", isStable = true, releasedAt = 1623329019000L)
            val actual = PackageVersionUtils.upgradeCandidateVersionOrNull(currentVersion, allVersionsList)
            val expected = PackageVersion.Named(versionName = "3.0.0-alpha01", isStable = false, releasedAt = 1757412548000L)
            assertEquals(expected, actual)
        }
    }

    @Nested
    inner class HighestVersionByName {

        @Test
        internal fun `should return the highest version by name regardless of timestamp, when it's a mix of stable versions and not`() {
            val actual = PackageVersionUtils.highestVersionByName(allVersionsList)
            val expected = PackageVersion.Named(versionName = "20190317.2000-alpha", isStable = false, releasedAt = 1132682960000L)
            assertEquals(expected, actual)
        }

        @Test
        internal fun `should return the highest version by name regardless of timestamp, when it's only stable versions`() {
            val actual = PackageVersionUtils.highestVersionByName(stableVersionsList)
            val expected = PackageVersion.Named(versionName = "20190316.1800", isStable = true, releasedAt = 1132682966666L)
            assertEquals(expected, actual)
        }

        @Test
        internal fun `should return the highest sensible version by name regardless of timestamp, when it's only sensible versions`() {
            val sensibleStableVersions = allVersionsList.filter { it.looksLikeASensibleVersionName() }
            val actual = PackageVersionUtils.highestVersionByName(sensibleStableVersions)
            val expected = PackageVersion.Named(versionName = "3.1-alpha01", isStable = false, releasedAt = 1132682950000L)
            assertEquals(expected, actual)
        }

        @Test
        internal fun `should return the highest sensible version by name regardless of timestamp, when it's only sensible stable versions`() {
            val sensibleStableVersions = stableVersionsList.filter { it.looksLikeASensibleVersionName() }
            val actual = PackageVersionUtils.highestVersionByName(sensibleStableVersions)
            val expected = PackageVersion.Named(versionName = "2.11.0", isStable = true, releasedAt = 1625881379000L)
            assertEquals(expected, actual)
        }

        @Test
        internal fun `should throw IAE when the list of versions is empty`() {
            assertThrows(IllegalArgumentException::class.java) { PackageVersionUtils.highestVersionByName(emptyList()) }
        }
    }

    @Nested
    inner class UnstableVersionsQualifiers {

        private val versionsList = loadFromRes("versions_list/many-unstable-versions.csv")

        @Test
        internal fun `should return the highest version by name regardless of timestamp, when there's many unstable milestone versions`() {
            val actual = PackageVersionUtils.highestVersionByName(versionsList)
            val expected = PackageVersion.Named(versionName = "2.124501.0", isStable = true, releasedAt = 1250046797000L)
            assertEquals(expected, actual)
        }

        @Test
        internal fun `should return the highest sensible version by name regardless of timestamp, when there's many unstable milestone versions`() {
            val actual = PackageVersionUtils.highestSensibleVersionByNameOrNull(versionsList)
            val expected = PackageVersion.Named(versionName = "2.13.0-RC3", isStable = false, releasedAt = 1559137791000L)
            assertEquals(expected, actual)
        }

        @Test
        internal fun `should return the highest sensible version as upgrade, when there's many unstable milestone versions`() {
            val currentVersion = PackageVersion.Named("2.13.0-RC2", false, 1558008423000L)
            val actual = PackageVersionUtils.upgradeCandidateVersionOrNull(currentVersion, versionsList)
            val expected = PackageVersion.Named(versionName = "2.13.0-RC3", isStable = false, releasedAt = 1559137791000L)
            assertEquals(expected, actual)
        }

        @Test
        internal fun `should return no upgrade for newer than in the list, when there's many unstable milestone versions`() {
            val currentVersion = PackageVersion.Named("2.14.0", true, 1668008423000L)
            val actual = PackageVersionUtils.upgradeCandidateVersionOrNull(currentVersion, versionsList)
            assertNull(actual)
        }

        @Test
        internal fun `should return no upgrade for newer (only by name) than in the list, when there's many unstable milestone versions`() {
            val currentVersion = PackageVersion.Named("2.14.0", true, 1459137791000L)
            val actual = PackageVersionUtils.upgradeCandidateVersionOrNull(currentVersion, versionsList)
            assertNull(actual)
        }
    }

    @Nested
    inner class HighestSensibleVersionByName {

        @Test
        internal fun `should return the highest sensible version by name regardless of timestamp, when it's a mix of stable versions and not`() {
            val actual = PackageVersionUtils.highestSensibleVersionByNameOrNull(allVersionsList)
            val expected = PackageVersion.Named(versionName = "3.1-alpha01", isStable = false, releasedAt = 1132682950000L)
            assertEquals(expected, actual)
        }

        @Test
        internal fun `should return the highest sensible version by name regardless of timestamp, when it's only stable versions`() {
            val actual = PackageVersionUtils.highestSensibleVersionByNameOrNull(stableVersionsList)
            val expected = PackageVersion.Named(versionName = "2.11.0", isStable = true, releasedAt = 1625881379000L)
            assertEquals(expected, actual)
        }

        @Test
        internal fun `should throw IAE when the list of versions is empty`() {
            assertThrows(IllegalArgumentException::class.java) { PackageVersionUtils.highestSensibleVersionByNameOrNull(emptyList()) }
        }
    }

    @Nested
    inner class SortWithHeuristicsDescending {

        @Test
        internal fun `should sort silly-looking versions by timestamp and not by name`() {
            val shuffledVersions = allVersionsList.shuffled()
            val sortedVersionsList = PackageVersionUtils.sortWithHeuristicsDescending(shuffledVersions)
            val expected = loadFromRes("versions_list/versions-with-edge-cases_sorted.csv")
            assertEquals(expected, sortedVersionsList)
        }
    }

    @Nested
    inner class RealWorldCases {

        @Test
        internal fun `should pick the right versions for apache commons-io`() {
            val versions = loadFromRes("versions_list/commons-io.csv")

            val sillyOldVersion = versions.first { it.versionName == "20030203.000550" }

            val expectedHighestByName = PackageVersion.Named(versionName = "20030203.000550", isStable = false, releasedAt = 1129356488000L)
            val expectedHighestSensibleByName = PackageVersion.Named(versionName = "2.11.0", isStable = true, releasedAt = 1625881379000L)

            assertEquals(expectedHighestByName, PackageVersionUtils.highestVersionByName(versions), "highest by name")

            assertEquals(
                expectedHighestSensibleByName,
                PackageVersionUtils.highestSensibleVersionByNameOrNull(versions),
                "highest sensible by name"
            )

            assertEquals(
                expectedHighestSensibleByName,
                PackageVersionUtils.upgradeCandidateVersionOrNull(sillyOldVersion, versions),
                "upgrade from ${sillyOldVersion.versionName}"
            )

            val v2_10_0 = PackageVersion.Named(versionName = "2.10.0", isStable = true, releasedAt = 1623329019000L)
            assertEquals(
                expectedHighestSensibleByName,
                PackageVersionUtils.upgradeCandidateVersionOrNull(v2_10_0, versions),
                "upgrade from ${v2_10_0.versionName}"
            )

            val v1_3_1 = PackageVersion.Named(versionName = "1.3.1", isStable = true, releasedAt = 1171391517000L)
            assertEquals(
                expectedHighestSensibleByName,
                PackageVersionUtils.upgradeCandidateVersionOrNull(v1_3_1, versions),
                "upgrade from ${v1_3_1.versionName}"
            )

            val v4_0_0_older = PackageVersion.Named(
                versionName = "4.0.0",
                isStable = true,
                releasedAt = expectedHighestSensibleByName.releasedAt!! - 1
            )
            assertNull(
                PackageVersionUtils.upgradeCandidateVersionOrNull(v4_0_0_older, versions),
                "no upgrade from ${v4_0_0_older.versionName} (older than highest sensible)"
            )

            val v4_0_0_newer = PackageVersion.Named(
                versionName = "4.0.0",
                isStable = true,
                releasedAt = expectedHighestSensibleByName.releasedAt!! + 1
            )
            assertNull(
                PackageVersionUtils.upgradeCandidateVersionOrNull(v4_0_0_newer, versions),
                "no upgrade from ${v4_0_0_newer.versionName} (newer than highest sensible)"
            )
        }

        @Test
        internal fun `should pick the right versions for scala-compiler`() {
            val versions = loadFromRes("versions_list/scala-compiler.csv")

            val expectedHighestByName = PackageVersion.Named(versionName = "2.13.6", isStable = true, releasedAt = 1621244016000L)
            val expectedHighestSensibleByName = PackageVersion.Named(versionName = "2.13.6", isStable = true, releasedAt = 1621244016000L)

            assertEquals(expectedHighestByName, PackageVersionUtils.highestVersionByName(versions), "highest by name")

            assertEquals(
                expectedHighestSensibleByName,
                PackageVersionUtils.highestSensibleVersionByNameOrNull(versions),
                "highest sensible by name"
            )

            val v2_13_5 = PackageVersion.Named(versionName = "2.13.5", isStable = true, releasedAt = 1614031188000L)
            assertEquals(
                expectedHighestSensibleByName,
                PackageVersionUtils.upgradeCandidateVersionOrNull(v2_13_5, versions),
                "upgrade from ${v2_13_5.versionName}"
            )

            val v2_13_0_M1 = PackageVersion.Named(versionName = "2.13.0-M1", isStable = false, releasedAt = 1492150729000L)
            assertEquals(
                expectedHighestSensibleByName,
                PackageVersionUtils.upgradeCandidateVersionOrNull(v2_13_0_M1, versions),
                "upgrade from ${v2_13_0_M1.versionName}"
            )

            val v2_10_0_whatever = PackageVersion.Named(
                versionName = "2.10.0-M1-virtualized.rdev-4217-2012-01-24-g9118644",
                isStable = false,
                releasedAt = 1327422783000L
            )
            assertEquals(
                expectedHighestSensibleByName,
                PackageVersionUtils.upgradeCandidateVersionOrNull(v2_10_0_whatever, versions),
                "upgrade from ${v2_10_0_whatever.versionName}"
            )

            val v2_4_0_RC1 = PackageVersion.Named(versionName = "2.4.0-RC1", isStable = false, releasedAt = 1227569762000L)
            assertEquals(
                expectedHighestSensibleByName,
                PackageVersionUtils.upgradeCandidateVersionOrNull(v2_4_0_RC1, versions),
                "upgrade from ${v2_4_0_RC1.versionName}"
            )

            val v4_0_0_older = PackageVersion.Named(
                versionName = "4.0.0",
                isStable = true,
                releasedAt = expectedHighestSensibleByName.releasedAt!! - 1
            )
            assertNull(
                PackageVersionUtils.upgradeCandidateVersionOrNull(v4_0_0_older, versions),
                "no upgrade from ${v4_0_0_older.versionName} (older than highest sensible)"
            )

            val v4_0_0_newer = PackageVersion.Named(
                versionName = "4.0.0",
                isStable = true,
                releasedAt = expectedHighestSensibleByName.releasedAt!! + 1
            )
            assertNull(
                PackageVersionUtils.upgradeCandidateVersionOrNull(v4_0_0_newer, versions),
                "no upgrade from ${v4_0_0_newer.versionName} (newer than highest sensible)"
            )
        }

        @Test
        internal fun `should pick the right versions for postgresql`() {
            val versions = loadFromRes("versions_list/postgresql.csv")

            val expectedHighestByName = PackageVersion.Named(versionName = "42.2.23.jre7", isStable = true, releasedAt = 1625584871000L)
            val expectedHighestSensibleByName = PackageVersion.Named(versionName = "42.2.23.jre7", isStable = true, releasedAt = 1625584871000L)

            assertEquals(expectedHighestByName, PackageVersionUtils.highestVersionByName(versions), "highest by name")

            assertEquals(
                expectedHighestSensibleByName,
                PackageVersionUtils.highestSensibleVersionByNameOrNull(versions),
                "highest sensible by name"
            )

            assertNull(
                PackageVersionUtils.upgradeCandidateVersionOrNull(expectedHighestByName, versions),
                "no upgrade for latest version"
            )

            val v42_2_23_jre6 = PackageVersion.Named(versionName = "42.2.23.jre6", isStable = true, releasedAt = 1625584871000L)
            assertNull(
                PackageVersionUtils.upgradeCandidateVersionOrNull(v42_2_23_jre6, versions),
                "no upgrade for latest version, different variant suffix"
            )

            val v42_2_23 = PackageVersion.Named(versionName = "42.2.23", isStable = true, releasedAt = 1625584855000L)
            assertNull(
                PackageVersionUtils.upgradeCandidateVersionOrNull(v42_2_23, versions),
                "no upgrade for latest version, no variant suffix"
            )

            val v42_2_22_jre7 = PackageVersion.Named(versionName = "42.2.22.jre7", isStable = true, releasedAt = 1623856041000L)
            assertEquals(
                expectedHighestSensibleByName,
                PackageVersionUtils.upgradeCandidateVersionOrNull(v42_2_22_jre7, versions),
                "upgrade from ${v42_2_22_jre7.versionName}"
            )

            val v42_2_22_jre6 = PackageVersion.Named(versionName = "42.2.22.jre6", isStable = true, releasedAt = 1623856038000L)
            assertEquals(
                expectedHighestSensibleByName,
                PackageVersionUtils.upgradeCandidateVersionOrNull(v42_2_22_jre6, versions),
                "upgrade from ${v42_2_22_jre6.versionName}"
            )

            val v42_2_22 = PackageVersion.Named(versionName = "42.2.22", isStable = true, releasedAt = 1623856038000L)
            assertEquals(
                expectedHighestSensibleByName,
                PackageVersionUtils.upgradeCandidateVersionOrNull(v42_2_22, versions),
                "upgrade from ${v42_2_22.versionName}"
            )

            val v42_2_21 = PackageVersion.Named(versionName = "42.2.21", isStable = true, releasedAt = 1623349399000L)
            assertEquals(
                expectedHighestSensibleByName,
                PackageVersionUtils.upgradeCandidateVersionOrNull(v42_2_21, versions),
                "upgrade from ${v42_2_21.versionName}"
            )
        }
    }

    private fun loadFromRes(path: String) =
        PackageSearchTestUtils.loadPackageVersionsFromResource(path)
}
