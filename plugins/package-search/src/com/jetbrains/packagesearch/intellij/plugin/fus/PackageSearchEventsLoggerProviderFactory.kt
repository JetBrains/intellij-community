package com.jetbrains.packagesearch.intellij.plugin.fus

import com.intellij.openapi.application.ApplicationInfo

object PackageSearchEventsLoggerProviderFactory {

    private fun isAvailable() =
        try {
            // API only available in 2019.2+
            val minimumSupportedMajorIJVersion = 2019
            val minimumSupportedMinorIJVersion = 2

            ApplicationInfo.getInstance().majorVersion.toInt() >= minimumSupportedMajorIJVersion &&
                ApplicationInfo.getInstance().minorVersion.toInt() >= minimumSupportedMinorIJVersion
        } catch (e: NumberFormatException) {
            false
        }

    fun create(): PackageSearchEventsLoggerProvider {
        if (isAvailable()) {
            return DefaultPackageSearchEventsLoggerProvider()
        }

        return NoopPackageSearchEventsLoggerProvider()
    }
}
