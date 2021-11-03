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
