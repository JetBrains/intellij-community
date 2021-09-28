package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.assertThat
import com.jetbrains.packagesearch.intellij.plugin.isEqualTo
import com.jetbrains.packagesearch.intellij.plugin.isZero
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PackageVersionComparatorTest {

    private val aNamedVersion = PackageVersion.Named("1.0.0", true, 1234567890123L)

    @Nested
    inner class CompareWithNullsTest {

        @Test
        internal fun `should return -1 if first is null and second is not`() {
            assertThat(PackageVersionComparator.compare(first = null, second = aNamedVersion)).isEqualTo(-1)
        }

        @Test
        internal fun `should return 1 if first is not null and second is`() {
            assertThat(PackageVersionComparator.compare(first = aNamedVersion, second = null)).isEqualTo(1)
        }

        @Test
        internal fun `should return 0 if both arguments are null`() {
            assertThat(PackageVersionComparator.compare(first = null, second = null)).isZero()
        }
    }

    @Nested
    inner class MissingVersionCompareTest {

        @Test
        internal fun `should return -1 if first is Missing and second is not`() {
            assertThat(PackageVersionComparator.compare(first = PackageVersion.Missing, second = aNamedVersion)).isEqualTo(-1)
        }

        @Test
        internal fun `should return 1 if first is not Missing and second is`() {
            assertThat(PackageVersionComparator.compare(first = aNamedVersion, second = PackageVersion.Missing)).isEqualTo(1)
        }

        @Test
        internal fun `should return 0 if both arguments are Missing`() {
            assertThat(PackageVersionComparator.compare(first = PackageVersion.Missing, second = PackageVersion.Missing)).isZero()
        }
    }

    @Nested
    inner class NamedVersionCompareTest {

        @Nested
        inner class PureSemanticVersions {

            // These are only straightforward cases as sanity checks, as we delegate to VersionComparatorUtil

            @Test
            internal fun `should return -1 when the first is lower than the second`() {
                val first = PackageVersion.Named("1.0.0", true, 1234567890123L)
                val second = PackageVersion.Named("2.0.0", true, 1234567890123L)

                assertThat(PackageVersionComparator.compare(first, second)).isEqualTo(-1)
            }

            @Test
            internal fun `should return 1 when the first is higher than the second`() {
                val first = PackageVersion.Named("2.0.0", true, 1234567890123L)
                val second = PackageVersion.Named("1.0.0", true, 1234567890123L)

                assertThat(PackageVersionComparator.compare(first, second)).isEqualTo(1)
            }

            @Test
            internal fun `should return 0 when the first is identical to the second`() {
                val first = PackageVersion.Named("1.0.0", true, 1234567890123L)
                val second = PackageVersion.Named("1.0.0", true, 1234567890123L)

                assertThat(PackageVersionComparator.compare(first, second)).isZero()
            }

            @Test
            internal fun `should return 0 when the first is semantically identical to the second`() {
                val first = PackageVersion.Named("1.0", true, 1234567890123L)
                val second = PackageVersion.Named("1.0.0", true, 1234567890123L)

                assertThat(PackageVersionComparator.compare(first, second)).isZero()
            }
        }
    }
}
