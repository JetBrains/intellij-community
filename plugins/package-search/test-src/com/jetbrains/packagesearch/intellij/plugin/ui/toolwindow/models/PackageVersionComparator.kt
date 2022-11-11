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

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.PackageVersionNormalizer
import kotlinx.coroutines.runBlocking

internal object PackageVersionComparator : Comparator<PackageVersion> {

    val normalizer = PackageVersionNormalizer()

    override fun compare(first: PackageVersion?, second: PackageVersion?): Int {
        when {
            first == null && second != null -> return -1
            first != null && second == null -> return 1
            first == null && second == null -> return 0
            first is PackageVersion.Missing && second !is PackageVersion.Missing -> return -1
            first !is PackageVersion.Missing && second is PackageVersion.Missing -> return 1
            first is PackageVersion.Missing && second is PackageVersion.Missing -> return 0
        }

        return compareNamed(first as PackageVersion.Named, second as PackageVersion.Named)
    }

    private fun compareNamed(first: PackageVersion.Named, second: PackageVersion.Named): Int {
        return runBlocking { normalizer.parse(first).compareTo(normalizer.parse(second)) }
    }
}
