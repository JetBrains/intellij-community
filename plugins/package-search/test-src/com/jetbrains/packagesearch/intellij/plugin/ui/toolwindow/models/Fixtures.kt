package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion

internal fun aNamedPackageVersion(
    versionName: String = "1.0.0-RC02-jre7",
    isStable: Boolean = true,
    releasedAt: Long? = 1234567890123L
) = PackageVersion.Named(versionName, isStable, releasedAt)

internal fun aSemanticVersion(
    original: PackageVersion.Named = aNamedPackageVersion(),
    semanticPart: String = "1.0.0",
    stabilityMarker: String? = "-RC02",
    nonSemanticSuffix: String? = "-jre7"
) = NormalizedPackageVersion.Semantic(original, semanticPart, stabilityMarker, nonSemanticSuffix)

internal fun aTimestampLikeVersion(
    original: PackageVersion.Named = aNamedPackageVersion("20090213003130-alpha1.banana"),
    timestampPrefix: String = "20090213003130",
    stabilityMarker: String? = "-alpha1",
    suffix: String? = ".banana"
) = NormalizedPackageVersion.TimestampLike(original, timestampPrefix, stabilityMarker, suffix)

internal fun aGarbageVersion(
    original: PackageVersion.Named = aNamedPackageVersion(versionName = "banana")
) = NormalizedPackageVersion.Garbage(original)
