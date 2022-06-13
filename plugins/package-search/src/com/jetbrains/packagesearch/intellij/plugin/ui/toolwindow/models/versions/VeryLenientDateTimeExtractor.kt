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

import java.text.ParsePosition
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

internal object VeryLenientDateTimeExtractor {

    /**
     * This list of patterns is sorted from longest to shortest. It's generated
     * by combining these base patterns:
     *  * `yyyy/MM/dd_HH:mm:ss`
     *  * `yyyy/MM/dd_HH:mm`
     *  * `yyyy/MM_HH:mm:ss`
     *  * `yyyy/MM_HH:mm`
     *  * `yyyy/MM/dd`
     *  * `yyyy/MM`
     *
     * With different dividers:
     *  * Date dividers: `.`, `-`, `[nothing]`
     *  * Time dividers: `.`, `-`, `[nothing]`
     *  * Date/time separator: `.`, `-`, `'T'`,`[nothing]`
     */
    private val datePatterns = listOf(
        "yyyy.MM.dd'T'HH.mm.ss",
        "yyyy.MM.dd'T'HH-mm-ss",
        "yyyy-MM-dd'T'HH.mm.ss",
        "yyyy-MM-dd'T'HH-mm-ss",
        "yyyy.MM.dd.HH.mm.ss",
        "yyyy.MM.dd-HH.mm.ss",
        "yyyy.MM.dd.HH-mm-ss",
        "yyyy.MM.dd-HH-mm-ss",
        "yyyy.MM.dd'T'HHmmss",
        "yyyy-MM-dd.HH.mm.ss",
        "yyyy-MM-dd-HH.mm.ss",
        "yyyy-MM-dd.HH-mm-ss",
        "yyyy-MM-dd-HH-mm-ss",
        "yyyy-MM-dd'T'HHmmss",
        "yyyyMMdd'T'HH.mm.ss",
        "yyyyMMdd'T'HH-mm-ss",
        "yyyy.MM.ddHH.mm.ss",
        "yyyy.MM.ddHH-mm-ss",
        "yyyy-MM-ddHH.mm.ss",
        "yyyy-MM-ddHH-mm-ss",
        "yyyy.MM.dd'T'HH.mm",
        "yyyy.MM.dd'T'HH-mm",
        "yyyy-MM-dd'T'HH.mm",
        "yyyy-MM-dd'T'HH-mm",
        "yyyy.MM'T'HH.mm.ss",
        "yyyy.MM'T'HH-mm-ss",
        "yyyy-MM'T'HH.mm.ss",
        "yyyy-MM'T'HH-mm-ss",
        "yyyy.MM.dd.HHmmss",
        "yyyy.MM.dd-HHmmss",
        "yyyy-MM-dd.HHmmss",
        "yyyy-MM-dd-HHmmss",
        "yyyyMMdd.HH.mm.ss",
        "yyyyMMdd-HH.mm.ss",
        "yyyyMMdd.HH-mm-ss",
        "yyyyMMdd-HH-mm-ss",
        "yyyyMMdd'T'HHmmss",
        "yyyy.MM.dd'T'HHmm",
        "yyyy-MM-dd'T'HHmm",
        "yyyyMM'T'HH.mm.ss",
        "yyyyMM'T'HH-mm-ss",
        "yyyy.MM.ddHHmmss",
        "yyyy-MM-ddHHmmss",
        "yyyyMMddHH.mm.ss",
        "yyyyMMddHH-mm-ss",
        "yyyy.MM.dd.HH.mm",
        "yyyy.MM.dd-HH.mm",
        "yyyy.MM.dd.HH-mm",
        "yyyy.MM.dd-HH-mm",
        "yyyy-MM-dd.HH.mm",
        "yyyy-MM-dd-HH.mm",
        "yyyy-MM-dd.HH-mm",
        "yyyy-MM-dd-HH-mm",
        "yyyyMMdd'T'HH.mm",
        "yyyyMMdd'T'HH-mm",
        "yyyy.MM.HH.mm.ss",
        "yyyy.MM-HH.mm.ss",
        "yyyy.MM.HH-mm-ss",
        "yyyy.MM-HH-mm-ss",
        "yyyy.MM'T'HHmmss",
        "yyyy-MM.HH.mm.ss",
        "yyyy-MM-HH.mm.ss",
        "yyyy-MM.HH-mm-ss",
        "yyyy-MM-HH-mm-ss",
        "yyyy-MM'T'HHmmss",
        "yyyyMMdd.HHmmss",
        "yyyyMMdd-HHmmss",
        "yyyy.MM.ddHH.mm",
        "yyyy.MM.ddHH-mm",
        "yyyy.MM.dd.HHmm",
        "yyyy.MM.dd-HHmm",
        "yyyy-MM-ddHH.mm",
        "yyyy-MM-ddHH-mm",
        "yyyy-MM-dd.HHmm",
        "yyyy-MM-dd-HHmm",
        "yyyyMMdd'T'HHmm",
        "yyyy.MMHH.mm.ss",
        "yyyy.MMHH-mm-ss",
        "yyyy-MMHH.mm.ss",
        "yyyy-MMHH-mm-ss",
        "yyyyMM.HH.mm.ss",
        "yyyyMM-HH.mm.ss",
        "yyyyMM.HH-mm-ss",
        "yyyyMM-HH-mm-ss",
        "yyyyMM'T'HHmmss",
        "yyyy.MM'T'HH.mm",
        "yyyy.MM'T'HH-mm",
        "yyyy-MM'T'HH.mm",
        "yyyy-MM'T'HH-mm",
        "yyyyMMddHHmmss",
        "yyyy.MM.ddHHmm",
        "yyyy-MM-ddHHmm",
        "yyyyMMdd.HH.mm",
        "yyyyMMdd-HH.mm",
        "yyyyMMdd.HH-mm",
        "yyyyMMdd-HH-mm",
        "yyyy.MM.HHmmss",
        "yyyy.MM-HHmmss",
        "yyyy-MM.HHmmss",
        "yyyy-MM-HHmmss",
        "yyyyMMHH.mm.ss",
        "yyyyMMHH-mm-ss",
        "yyyy.MM'T'HHmm",
        "yyyy-MM'T'HHmm",
        "yyyyMM'T'HH.mm",
        "yyyyMM'T'HH-mm",
        "yyyyMMddHH.mm",
        "yyyyMMddHH-mm",
        "yyyyMMdd.HHmm",
        "yyyyMMdd-HHmm",
        "yyyy.MMHHmmss",
        "yyyy-MMHHmmss",
        "yyyyMM.HHmmss",
        "yyyyMM-HHmmss",
        "yyyy.MM.HH.mm",
        "yyyy.MM-HH.mm",
        "yyyy.MM.HH-mm",
        "yyyy.MM-HH-mm",
        "yyyy-MM.HH.mm",
        "yyyy-MM-HH.mm",
        "yyyy-MM.HH-mm",
        "yyyy-MM-HH-mm",
        "yyyyMM'T'HHmm",
        "yyyyMMddHHmm",
        "yyyyMMHHmmss",
        "yyyy.MMHH.mm",
        "yyyy.MMHH-mm",
        "yyyy.MM.HHmm",
        "yyyy.MM-HHmm",
        "yyyy-MMHH.mm",
        "yyyy-MMHH-mm",
        "yyyy-MM.HHmm",
        "yyyy-MM-HHmm",
        "yyyyMM.HH.mm",
        "yyyyMM-HH.mm",
        "yyyyMM.HH-mm",
        "yyyyMM-HH-mm",
        "yyyy.MMHHmm",
        "yyyy-MMHHmm",
        "yyyyMMHH.mm",
        "yyyyMMHH-mm",
        "yyyyMM.HHmm",
        "yyyyMM-HHmm",
        "yyyyMMHHmm",
        "yyyy.MM.dd",
        "yyyy-MM-dd",
        "yyyyMMdd",
        "yyyy.MM",
        "yyyy-MM",
        "yyyyMM",
    )

    private val formatters: List<DateTimeFormatter> = datePatterns.map {
        DateTimeFormatterBuilder().appendPattern(it).toFormatter(Locale.ENGLISH)
    }

    /**
     * The current year. Note that this value can, potentially, get out of date
     * if the JVM is started on year X and is still running when transitioning
     * to year Y. To ensure we don't have such bugs we should always add in a
     * certain "tolerance" when checking the year. We also assume the plugin will
     * not be left running for more than a few months (we release IDE versions
     * much more frequently than that), so having a 1-year tolerance should be
     * enough. We also expect the device clock is not more than 1-year off from
     * the real date, given one would have to go out of their way to make it so,
     * and plenty of things will break.
     *
     * ...yes, famous last words.
     */
    private val thisYear = LocalDate.now().year

    fun extractTimestampLookingPrefixOrNull(versionName: String) = formatters.asSequence()
        .mapNotNull {
            val parsePosition = ParsePosition(0)
            val result = kotlin.runCatching { it.parse(versionName, parsePosition) }
            val accessor = result.getOrNull() ?: return@mapNotNull null

            // It's extremely unlikely timestamps would refer to a date in the future.
            // This is here to catch things that look like dates but are too far in the
            // future to actually make sense.
            if (accessor.get(ChronoField.YEAR) > thisYear + 1) return@mapNotNull null
            versionName.substring(0 until parsePosition.index)
        }
        .firstOrNull()
}
