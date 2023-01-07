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

/**
 * Used as parameter for the [VersionComparatorUtil.compare] method, to include additional tokens in the comparison.
 *
 * We support the following stability tokens, in addition to the ones supported by [VersionComparatorUtil]:
 * * For snapshot-level: `snapshots`, `s`, `p`, `develop`, `dev`
 * * Milestone-level: `milestone`, `build`
 * * RC-level: `candidate`
 * * Release-level: `stable`
 *
 * @see com.intellij.util.text.VersionComparatorUtil.VersionTokenType
 */
internal fun versionTokenPriorityProvider(token: String?): Int {
    val tokenType = VersionComparatorUtil.VersionTokenType.lookup(token)
    if (tokenType != VersionComparatorUtil.VersionTokenType._WORD) return tokenType.priority

    if (token == null) return VersionComparatorUtil.VersionTokenType._WS.priority
    val normalizedToken = token.trim().uppercase()
    return AdditionalTokenTypes.values().find { it.name == normalizedToken }?.priority
        ?: VersionComparatorUtil.VersionTokenType._WORD.priority
}

private enum class AdditionalTokenTypes(val priority: Int) {

    SNAPSHOTS(10),
    S(10),
    DEV(10),
    DEVELOP(10),
    BUILD(20),
    MILESTONE(20),
    CANDIDATE(80),
    STABLE(80)
}
