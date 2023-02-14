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

package com.jetbrains.packagesearch.intellij.plugin

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion

internal object PackageSearchTestUtils {

    fun loadPackageVersionsFromResource(resourcePath: String): List<PackageVersion.Named> =
        javaClass.classLoader.getResourceAsStream(resourcePath)!!
            .bufferedReader()
            .useLines { lines ->
                lines.filterNot { it.startsWith('#') }
                    .mapIndexed { lineNumber, line ->
                        val parts = line.split(',')
                        check(parts.size == 3) { "Invalid line ${lineNumber + 1}: has ${parts.size} part(s), but we expected to have 3" }

                        val versionName = parts[0]
                        val isStable = parts[1].toBooleanStrictOrNull()
                            ?: error("Line ${lineNumber + 1} has invalid stability: ${parts[1]}")
                        val timestamp = parts[2].toLongOrNull()
                            ?: error("Line ${lineNumber + 1} has an invalid timestamp: ${parts[1]}")

                        PackageVersion.Named(versionName, isStable, timestamp)
                    }
                    .toList()
            }
}
