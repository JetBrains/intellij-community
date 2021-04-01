package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.util.text.VersionComparatorUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Version
import com.jetbrains.packagesearch.intellij.plugin.version.looksLikeStableVersion
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

internal sealed class PackageVersion(
    open val versionName: String,
    open val isStable: Boolean
) : Comparable<PackageVersion> {

    @get:Nls
    abstract val displayName: String

    override fun compareTo(other: PackageVersion): Int =
        VersionComparatorUtil.compare(versionName, other.versionName)

    object Missing : PackageVersion("", isStable = true) {

        @Nls
        override val displayName = PackageSearchBundle.message("packagesearch.ui.missingVersion")

        @NonNls
        override fun toString() = "[Missing version]"
    }

    data class Named(override val versionName: String, override val isStable: Boolean) : PackageVersion(versionName, isStable) {

        init {
            require(versionName.isNotBlank()) { "A Named version name cannot be blank." }
        }

        @Suppress("HardCodedStringLiteral")
        @Nls
        override val displayName = versionName

        @NonNls
        override fun toString() = versionName
    }

    companion object {

        fun from(rawVersion: StandardV2Version): PackageVersion {
            if (rawVersion.version.isBlank()) return Missing
            return Named(rawVersion.version.trim(), rawVersion.stable)
        }

        fun from(rawVersion: String?): PackageVersion {
            if (rawVersion.isNullOrBlank()) return Missing
            return Named(rawVersion.trim(), isStable = looksLikeStableVersion(rawVersion))
        }
    }
}
