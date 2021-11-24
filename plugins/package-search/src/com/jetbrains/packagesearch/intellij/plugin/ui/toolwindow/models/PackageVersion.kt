package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.util.text.VersionComparatorUtil
import com.jetbrains.packagesearch.api.v2.ApiStandardPackage
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.util.versionTokenPriorityProvider
import com.jetbrains.packagesearch.packageversionutils.PackageVersionUtils
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

@Serializable
sealed class PackageVersion : Comparable<PackageVersion> {

    abstract val versionName: String
    abstract val isStable: Boolean
    abstract val releasedAt: Long?

    @get:Nls
    abstract val displayName: String

    override fun compareTo(other: PackageVersion): Int =
        VersionComparatorUtil.compare(versionName, other.versionName, ::versionTokenPriorityProvider)

    @Serializable
    object Missing : PackageVersion() {

        override val versionName = ""
        override val isStable = true
        override val releasedAt: Long? = null

        @Nls
        override val displayName = PackageSearchBundle.message("packagesearch.ui.missingVersion")

        @NonNls
        override fun toString() = "[Missing version]"
    }

    @Serializable
    data class Named(
        override val versionName: String,
        override val isStable: Boolean,
        override val releasedAt: Long?
    ) : PackageVersion() {

        init {
            require(versionName.isNotBlank()) { "A Named version name cannot be blank." }
        }

        @Suppress("HardCodedStringLiteral")
        @Nls
        override val displayName = versionName

        @NonNls
        override fun toString() = versionName

        internal fun semanticVersionComponent(): SemVerComponent? {
            val groupValues = SEMVER_REGEX.find(versionName)?.groupValues ?: return null
            if (groupValues.size <= 1) return null
            val semanticVersion = groupValues[1].takeIf { it.isNotBlank() } ?: return null
            return SemVerComponent(semanticVersion, this)
        }

        internal data class SemVerComponent(val semanticVersion: String, val named: Named)

        companion object {

            private val SEMVER_REGEX = "^((?:\\d+\\.){0,3}\\d+)".toRegex(option = RegexOption.IGNORE_CASE)
        }
    }

    companion object {

        internal fun from(rawVersion: ApiStandardPackage.ApiStandardVersion): PackageVersion {
            if (rawVersion.version.isBlank()) return Missing
            return Named(versionName = rawVersion.version.trim(), isStable = rawVersion.stable, releasedAt = rawVersion.lastChanged)
        }

        fun from(rawVersion: String?): PackageVersion {
            if (rawVersion.isNullOrBlank()) return Missing
            return Named(rawVersion.trim(), isStable = PackageVersionUtils.evaluateStability(rawVersion), releasedAt = null)
        }
    }
}
