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

package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.util.text.VersionComparatorUtil
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion

/**
 * Compares two raw version names, best-effort. If at all possible, you should use the [NormalizedPackageVersion] to compare versions with a lot of
 * additional heuristics, including prioritizing semantic version names over non-semantic ones.
 *
 * This expands on what [VersionComparatorUtil] provide, so you should prefer this to [VersionComparatorUtil.COMPARATOR].
 *
 * @see versionTokenPriorityProvider
 * @see VersionComparatorUtil
 */
object VersionNameComparator : Comparator<String> {

    override fun compare(first: String?, second: String?): Int =
        VersionComparatorUtil.compare(first, second, ::versionTokenPriorityProvider)
}
