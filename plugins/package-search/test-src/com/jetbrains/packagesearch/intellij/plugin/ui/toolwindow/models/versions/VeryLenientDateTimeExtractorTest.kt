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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions

import com.jetbrains.packagesearch.intellij.plugin.assertThat
import com.jetbrains.packagesearch.intellij.plugin.isEqualTo
import com.jetbrains.packagesearch.intellij.plugin.isNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource

internal class VeryLenientDateTimeExtractorTest {

    @ParameterizedTest
    @CsvFileSource(resources = ["/invalid-date-time-prefixes.csv"])
    internal fun `should return null when there is no datetime-like prefix`(versionName: String) {
        val looksLikeATimestamp = VeryLenientDateTimeExtractor.extractTimestampLookingPrefixOrNull(versionName)
        assertThat(looksLikeATimestamp).isNull()
    }

    @ParameterizedTest
    @CsvFileSource(resources = ["/valid-date-time-prefixes.csv"])
    internal fun `should return the prefix when there is a datetime-like prefix`(versionName: String, expected: String) {
        assertThat(VeryLenientDateTimeExtractor.extractTimestampLookingPrefixOrNull(versionName)).isEqualTo(expected)
    }
}
