/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.util.text.VersionComparatorUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.util.versionTokenPriorityProvider
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage
import org.jetbrains.packagesearch.packageversionutils.PackageVersionUtils

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

        fun semanticVersionComponent(): SemVerComponent? {
            val groupValues = SEMVER_REGEX.find(versionName)?.groupValues ?: return null
            if (groupValues.size <= 1) return null
            val semanticVersion = groupValues[1].takeIf { it.isNotBlank() } ?: return null
            return SemVerComponent(semanticVersion, this)
        }

        data class SemVerComponent(val semanticVersion: String, val named: Named)

        companion object {

            private val SEMVER_REGEX = "^((?:\\d+\\.){0,3}\\d+)".toRegex(option = RegexOption.IGNORE_CASE)
        }
    }

    companion object {

        fun from(rawVersion: ApiStandardPackage.ApiStandardVersion): PackageVersion {
            if (rawVersion.version.isBlank()) return Missing
            return Named(versionName = rawVersion.version.trim(), isStable = rawVersion.stable, releasedAt = rawVersion.lastChanged)
        }

        fun from(rawVersion: String?): PackageVersion {
            if (rawVersion.isNullOrBlank()) return Missing
            return Named(rawVersion.trim(), isStable = PackageVersionUtils.evaluateStability(rawVersion), releasedAt = null)
        }
    }
}
