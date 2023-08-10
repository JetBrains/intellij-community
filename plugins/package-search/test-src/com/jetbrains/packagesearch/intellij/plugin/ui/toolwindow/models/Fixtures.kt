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
