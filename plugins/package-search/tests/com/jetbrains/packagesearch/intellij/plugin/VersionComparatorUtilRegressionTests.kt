package com.jetbrains.packagesearch.intellij.plugin

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isNegative
import assertk.assertions.isPositive
import assertk.assertions.isZero
import com.intellij.util.text.VersionComparatorUtil
import org.junit.jupiter.api.Test

class VersionComparatorUtilRegressionTests {

    @Test
    fun `regression test for KPM-200`() {
        assertThat("4.0.3-83f81a8-20180227163433").comparesAsLowerThan("4.0.4-3593406-20180327185327")
        assertThat("4.0.3-12345678-20180227163433").comparesAsLowerThan("4.0.4-3593406-20180327185327")

        assertThat("4.0.3").comparesAsLowerThan("4.0.4-3593406-20180327185327")
        assertThat("4.0.3-12345678").comparesAsLowerThan("4.0.4-3593406-20180327185327")
        assertThat("4.0.3-12345678-20180227163433").comparesAsLowerThan("4.0.4-3593406-20180327185327")

        assertThat("4.0.3-83f81a8-20180227163433").comparesAsEqualTo("4.0.3-83f81a8-20180227163433")
        assertThat("4.0.3-12345678-20180227163433").comparesAsEqualTo("4.0.3-12345678-20180227163433")
        assertThat("4.0.3-12345678").comparesAsEqualTo("4.0.3-12345678")
        assertThat("4.0.3").comparesAsEqualTo("4.0.3")
        assertThat("4.0.4-3593406-20180327185327").comparesAsEqualTo("4.0.4-3593406-20180327185327")
    }

    private fun Assert<String?>.comparesAsLowerThan(other: String?) = given {
        assertThat(VersionComparatorUtil.compare(it, other)).isNegative()
        assertThat(VersionComparatorUtil.compare(other, it)).isPositive()
    }

    private fun Assert<String?>.comparesAsEqualTo(other: String) = given {
        assertThat(VersionComparatorUtil.compare(it, other)).isZero()
    }
}
